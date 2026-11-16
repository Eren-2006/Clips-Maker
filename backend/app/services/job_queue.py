from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from app.config import settings
from app.models import (
    ClipMoment,
    ClipResult,
    JobErrorCode,
    JobStatusResponse,
    PipelineStep,
    TranscriptSegment,
    WordTimestamp,
)
from app.services.moment_selector import select_best_moments_llm
from app.services.transcriber import extract_audio_wav, transcribe_audio_whisper
from app.services.video_editor import create_clips_zip, render_short_clip
from app.services.youtube import YouTubeServiceError, fetch_and_download_video, validate_youtube_url


@dataclass
class JobState:
    job_id: str
    youtube_url: str
    mode: str
    job_dir: Path
    target_language: str = "auto"
    status: str = "queued"
    step: PipelineStep = PipelineStep.DOWNLOADING
    progress_percent: int = 0
    status_message: str = "Queued for processing..."
    video_title: Optional[str] = None
    video_duration_seconds: float = 0.0
    detected_language: Optional[str] = None
    words: list[WordTimestamp] = field(default_factory=list)
    transcript_segments: list[TranscriptSegment] = field(default_factory=list)
    source_video_path: Optional[Path] = None
    moments_event: asyncio.Event = field(default_factory=asyncio.Event)
    submitted_moments: list[ClipMoment] = field(default_factory=list)
    clips: list[ClipResult] = field(default_factory=list)
    zip_path: Optional[Path] = None
    error_code: Optional[JobErrorCode] = None
    error_message: Optional[str] = None


class SingleWorkerJobManager:
    """Processes video clipping jobs strictly one at a time in a FIFO background queue."""

    def __init__(self, output_dir: Path | None = None) -> None:
        self.output_dir = output_dir or settings.output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.jobs: dict[str, JobState] = {}
        self.queue: asyncio.Queue[str] = asyncio.Queue()
        self.pending_order: list[str] = []
        self.active_job_id: Optional[str] = None
        self._worker_task: Optional[asyncio.Task] = None

    def start(self) -> None:
        if self._worker_task is None or self._worker_task.done():
            self._worker_task = asyncio.create_task(self._worker_loop())

    async def stop(self) -> None:
        if self._worker_task and not self._worker_task.done():
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass

    def enqueue_job(
        self,
        youtube_url: str,
        mode: str = "server_ai",
        target_language: str = "auto",
    ) -> tuple[str, int]:
        normalized = validate_youtube_url(youtube_url)
        job_id = uuid.uuid4().hex[:12]
        job_dir = self.output_dir / job_id
        job_dir.mkdir(parents=True, exist_ok=True)

        state = JobState(
            job_id=job_id,
            youtube_url=normalized,
            mode=mode,
            job_dir=job_dir,
            target_language=target_language,
        )
        self.jobs[job_id] = state
        self.pending_order.append(job_id)
        position = len(self.pending_order) + (1 if self.active_job_id else 0) - 1
        self.queue.put_nowait(job_id)
        return job_id, max(0, position)

    def submit_client_moments(self, job_id: str, moments: list[ClipMoment]) -> JobStatusResponse:
        job = self.jobs.get(job_id)
        if not job:
            raise KeyError(job_id)
        job.submitted_moments = moments
        job.step = PipelineStep.EDITING_CLIPS
        job.status = "running"
        job.progress_percent = 60
        job.status_message = f"Rendering {len(moments)} vertical 9:16 clips..."
        job.moments_event.set()
        return self.get_status(job_id)

    def get_status(self, job_id: str) -> JobStatusResponse:
        job = self.jobs.get(job_id)
        if not job:
            raise KeyError(job_id)

        queue_pos = 0
        if job.status == "queued" and job_id in self.pending_order:
            queue_pos = self.pending_order.index(job_id) + (1 if self.active_job_id else 0)

        return JobStatusResponse(
            job_id=job.job_id,
            status=job.status,
            step=job.step,
            progress_percent=job.progress_percent,
            status_message=job.status_message,
            queue_position=queue_pos,
            video_title=job.video_title,
            video_duration_seconds=job.video_duration_seconds,
            detected_language=job.detected_language,
            transcript_segments=job.transcript_segments if job.status == "awaiting_moments" else [],
            clips=job.clips,
            zip_url=f"/api/jobs/{job.job_id}/zip" if job.zip_path and job.zip_path.exists() else None,
            error_code=job.error_code,
            error_message=job.error_message,
        )

    async def _worker_loop(self) -> None:
        while True:
            job_id = await self.queue.get()
            if job_id in self.pending_order:
                self.pending_order.remove(job_id)
            self.active_job_id = job_id
            try:
                await self._process_job(self.jobs[job_id])
            except Exception as exc:
                job = self.jobs.get(job_id)
                if job and job.status != "failed":
                    job.status = "failed"
                    job.step = PipelineStep.ERROR
                    job.error_code = JobErrorCode.PIPELINE_FAILURE
                    job.error_message = str(exc)
            finally:
                self.active_job_id = None
                self.queue.task_done()

    async def _process_job(self, job: JobState) -> None:
        loop = asyncio.get_running_loop()
        try:
            # Step 1: Download with yt-dlp
            job.status = "running"
            job.step = PipelineStep.DOWNLOADING
            job.progress_percent = 5
            job.status_message = "Checking video metadata & downloading up to 1080p..."

            def _on_dl_progress(pct: int, msg: str) -> None:
                job.progress_percent = pct
                job.status_message = msg

            metadata, video_path = await loop.run_in_executor(
                None,
                fetch_and_download_video,
                job.youtube_url,
                job.job_dir,
                _on_dl_progress,
            )
            job.video_title = metadata.title
            job.video_duration_seconds = metadata.duration_seconds
            job.source_video_path = video_path

            # Step 2: Audio extraction + OpenAI Whisper transcription
            job.step = PipelineStep.TRANSCRIBING
            job.progress_percent = 32
            if job.target_language in {"en", "hi", "bn"}:
                job.status_message = f"Extracting audio & transcribing with Whisper ({job.target_language.upper()})..."
            else:
                job.status_message = "Extracting audio & transcribing with Whisper (EN/HI/BN)..."
            wav_path = job.job_dir / "audio.wav"
            await loop.run_in_executor(None, extract_audio_wav, video_path, wav_path)

            lang, words, segments = await loop.run_in_executor(
                None,
                transcribe_audio_whisper,
                wav_path,
                job.target_language,
            )
            job.detected_language = lang
            job.words = words
            job.transcript_segments = segments

            # Step 3: Best moment selection
            job.step = PipelineStep.FINDING_MOMENTS
            job.progress_percent = 52

            if job.mode == "client_ai":
                job.status = "awaiting_moments"
                job.status_message = "Waiting for Gemini AI moment selection..."
                await asyncio.wait_for(job.moments_event.wait(), timeout=180.0)
                moments = job.submitted_moments
            else:
                job.status_message = "Finding 5–10 best viral moments with AI..."
                moments = await loop.run_in_executor(
                    None,
                    select_best_moments_llm,
                    metadata.title,
                    lang,
                    metadata.duration_seconds,
                    segments,
                    job.target_language,
                )

            # Step 4: Render 9:16 clips with FFmpeg + OpenCV/MediaPipe + ASS captions + loudnorm
            job.status = "running"
            job.step = PipelineStep.EDITING_CLIPS
            clips_dir = job.job_dir / "clips"
            clips_dir.mkdir(parents=True, exist_ok=True)
            rendered_files: list[Path] = []
            rendered_results: list[ClipResult] = []

            total = max(1, len(moments))
            for idx, moment in enumerate(moments, start=1):
                clip_id = f"clip_{idx:02d}"
                job.progress_percent = 58 + int((idx - 1) / total * 38)
                job.status_message = f"Editing clip {idx}/{total}: {moment.title[:36]}..."
                out_mp4 = clips_dir / f"{clip_id}.mp4"
                result = await loop.run_in_executor(
                    None,
                    render_short_clip,
                    video_path,
                    moment,
                    words,
                    lang,
                    out_mp4,
                    clips_dir,
                    clip_id,
                )
                result.preview_url = f"/api/jobs/{job.job_id}/clips/{clip_id}"
                result.download_url = f"/api/jobs/{job.job_id}/clips/{clip_id}?download=1"
                rendered_files.append(out_mp4)
                rendered_results.append(result)

            zip_path = job.job_dir / f"clipmaster_{job.job_id}_clips.zip"
            await loop.run_in_executor(None, create_clips_zip, rendered_files, zip_path)

            # Step 5: Done
            job.clips = rendered_results
            job.zip_path = zip_path
            job.step = PipelineStep.DONE
            job.status = "completed"
            job.progress_percent = 100
            job.status_message = f"Done! {len(rendered_results)} ready-to-post shorts created."

        except YouTubeServiceError as exc:
            job.status = "failed"
            job.step = PipelineStep.ERROR
            job.error_code = exc.code
            job.error_message = exc.message
            job.status_message = exc.message
        except asyncio.TimeoutError:
            job.status = "failed"
            job.step = PipelineStep.ERROR
            job.error_code = JobErrorCode.API_FAILURE
            job.error_message = "Timed out waiting for AI moment selection."
            job.status_message = job.error_message
        except Exception as exc:
            job.status = "failed"
            job.step = PipelineStep.ERROR
            job.error_code = JobErrorCode.PIPELINE_FAILURE
            job.error_message = str(exc)
            job.status_message = str(exc)
