from __future__ import annotations

import json
import subprocess
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from app.models import ClipMoment, ClipResult, JobErrorCode, WordTimestamp
from app.services.youtube import YouTubeServiceError

TARGET_WIDTH = 1080
TARGET_HEIGHT = 1920


@dataclass
class VideoDimensions:
    width: int
    height: int


def probe_video_dimensions(video_path: Path) -> VideoDimensions:
    cmd = [
        "ffprobe",
        "-v",
        "error",
        "-select_streams",
        "v:0",
        "-show_entries",
        "stream=width,height",
        "-of",
        "json",
        str(video_path),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        return VideoDimensions(width=1920, height=1080)
    try:
        data = json.loads(proc.stdout)
        stream = data["streams"][0]
        return VideoDimensions(width=int(stream["width"]), height=int(stream["height"]))
    except Exception:
        return VideoDimensions(width=1920, height=1080)


def detect_speaker_crop_x(
    video_path: Path,
    start: float,
    end: float,
    dims: VideoDimensions,
) -> Optional[int]:
    """Detect active speaker horizontal face center across sampled frames.
    Returns horizontal crop offset (crop_x) for 9:16 crop, or None if no face is detected.
    """
    crop_w = min(dims.width, int(dims.height * 9 / 16))
    if crop_w <= 0 or dims.width <= crop_w:
        return None

    centers: list[float] = []
    try:
        import cv2
    except ImportError:
        return None

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return None

    mp_detector = None
    try:
        import mediapipe as mp
        mp_detector = mp.solutions.face_detection.FaceDetection(
            model_selection=1,
            min_detection_confidence=0.5,
        )
    except Exception:
        mp_detector = None

    haar = cv2.CascadeClassifier(
        cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
    )

    try:
        sample_count = 8
        duration = max(1.0, end - start)
        for i in range(sample_count):
            t_ms = (start + (duration * (i + 0.5) / sample_count)) * 1000.0
            cap.set(cv2.CAP_PROP_POS_MSEC, t_ms)
            ok, frame = cap.read()
            if not ok or frame is None:
                continue

            if mp_detector is not None:
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                res = mp_detector.process(rgb)
                if res.detections:
                    best = max(
                        res.detections,
                        key=lambda d: (
                            d.location_data.relative_bounding_box.width
                            * d.location_data.relative_bounding_box.height
                        ),
                    )
                    box = best.location_data.relative_bounding_box
                    cx = (box.xmin + box.width * 0.5) * dims.width
                    centers.append(cx)
                    continue

            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = haar.detectMultiScale(gray, scaleFactor=1.15, minNeighbors=5, minSize=(48, 48))
            if len(faces) > 0:
                x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
                centers.append(float(x + w * 0.5))
    finally:
        cap.release()
        if mp_detector is not None:
            mp_detector.close()

    if len(centers) < 2:
        return None

    centers.sort()
    median_cx = centers[len(centers) // 2]
    crop_x = int(round(median_cx - crop_w / 2.0))
    crop_x = max(0, min(dims.width - crop_w, crop_x))
    return crop_x


def _format_ass_time(seconds: float) -> str:
    clamped = max(0.0, seconds)
    hours = int(clamped // 3600)
    minutes = int((clamped % 3600) // 60)
    secs = int(clamped % 60)
    centis = int(round((clamped - int(clamped)) * 100))
    if centis >= 100:
        secs += 1
        centis -= 100
    return f"{hours}:{minutes:02d}:{secs:02d}.{centis:02d}"


def _escape_ass_text(text: str) -> str:
    return text.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")


def _choose_caption_font(language: str, text_sample: str) -> str:
    if language == "bn" or any("\u0980" <= ch <= "\u09ff" for ch in text_sample):
        return "Noto Sans Bengali"
    if language == "hi" or any("\u0900" <= ch <= "\u097f" for ch in text_sample):
        return "Noto Sans Devanagari"
    return "Noto Sans"


def build_ass_captions(
    words: list[WordTimestamp],
    clip_start: float,
    clip_end: float,
    language: str,
    output_ass_path: Path,
) -> Path:
    """Create animated word-by-word ASS captions:
    - Large bold font with safe 9:16 margins
    - Max 2 lines per phrase
    - Currently spoken word highlighted in bright yellow (#FACC15 -> &H0015CCFA&)
    - Uses Noto Sans Bengali when Bengali script/language is active
    """
    clip_words = [
        w for w in words
        if w.end > clip_start and w.start < clip_end and w.word.strip()
    ]
    sample_text = " ".join(w.word for w in clip_words)
    font_name = _choose_caption_font(language, sample_text)

    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {TARGET_WIDTH}
PlayResY: {TARGET_HEIGHT}
WrapStyle: 2
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,{font_name},66,&H00FFFFFF,&H0015CCFA,&H000A0B0E,&H96000000,-1,0,0,0,100,100,0,0,1,6,2,2,72,72,260,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

    events: list[str] = []
    # Group words into chunks of up to 6 words (fits comfortably in <= 2 lines at 66pt)
    chunk_size = 6
    chunks = [clip_words[i:i + chunk_size] for i in range(0, len(clip_words), chunk_size)]

    for chunk in chunks:
        midpoint = len(chunk) // 2
        for active_idx, active_word in enumerate(chunk):
            w_start = max(0.0, active_word.start - clip_start)
            if active_idx + 1 < len(chunk):
                w_end = max(w_start + 0.08, chunk[active_idx + 1].start - clip_start)
            else:
                w_end = max(w_start + 0.12, min(clip_end - clip_start, active_word.end - clip_start))

            tokens: list[str] = []
            for idx, w in enumerate(chunk):
                if idx == midpoint and len(chunk) >= 4:
                    tokens.append("\\N")
                safe_word = _escape_ass_text(w.word.strip())
                if idx == active_idx:
                    tokens.append(f"{{\\c&H0015CCFA&\\fscx108\\fscy108}}{safe_word}{{\\c&H00FFFFFF&\\fscx100\\fscy100}}")
                else:
                    tokens.append(safe_word)

            line_text = " ".join(tokens).replace(" \\N ", "\\N")
            events.append(
                f"Dialogue: 0,{_format_ass_time(w_start)},{_format_ass_time(w_end)},Default,,0,0,0,,{line_text}"
            )

    output_ass_path.write_text(header + "\n".join(events) + "\n", encoding="utf-8")
    return output_ass_path


def _escape_ffmpeg_filter_path(path: Path) -> str:
    return path.resolve().as_posix().replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")


def build_video_filter(
    dims: VideoDimensions,
    crop_x: Optional[int],
    ass_path: Optional[Path] = None,
) -> str:
    if crop_x is not None:
        crop_w = min(dims.width, int(dims.height * 9 / 16))
        crop_h = dims.height
        # Ensure even dimensions for x264
        crop_w -= crop_w % 2
        crop_h -= crop_h % 2
        base_chain = f"crop={crop_w}:{crop_h}:{crop_x}:0,scale={TARGET_WIDTH}:{TARGET_HEIGHT},setsar=1"
    else:
        base_chain = (
            f"split=2[bg][fg];"
            f"[bg]scale={TARGET_WIDTH}:{TARGET_HEIGHT}:force_original_aspect_ratio=increase,"
            f"crop={TARGET_WIDTH}:{TARGET_HEIGHT},boxblur=25:5[bgb];"
            f"[fg]scale={TARGET_WIDTH}:-2:force_original_aspect_ratio=decrease[fgs];"
            f"[bgb][fgs]overlay=(W-w)/2:(H-h)/2,setsar=1"
        )

    if ass_path is not None and ass_path.exists():
        escaped = _escape_ffmpeg_filter_path(ass_path)
        if crop_x is not None:
            return f"{base_chain},subtitles='{escaped}'"
        return f"{base_chain}[vpre];[vpre]subtitles='{escaped}'"
    return base_chain


def render_short_clip(
    source_video: Path,
    moment: ClipMoment,
    words: list[WordTimestamp],
    language: str,
    output_mp4: Path,
    work_dir: Path,
    clip_id: str,
) -> ClipResult:
    dims = probe_video_dimensions(source_video)
    crop_x = detect_speaker_crop_x(source_video, moment.start, moment.end, dims)

    ass_path = work_dir / f"{clip_id}.ass"
    build_ass_captions(
        words=words,
        clip_start=moment.start,
        clip_end=moment.end,
        language=language,
        output_ass_path=ass_path,
    )

    vf_graph = build_video_filter(dims=dims, crop_x=crop_x, ass_path=ass_path)
    duration = max(1.0, moment.end - moment.start)

    cmd = [
        "ffmpeg",
        "-y",
        "-ss",
        f"{moment.start:.3f}",
        "-t",
        f"{duration:.3f}",
        "-i",
        str(source_video),
        "-filter_complex" if ";" in vf_graph else "-vf",
        vf_graph,
        "-af",
        "loudnorm=I=-14:TP=-1.5:LRA=11",
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "22",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "128k",
        "-movflags",
        "+faststart",
        str(output_mp4),
    ]

    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0 or not output_mp4.exists():
        raise YouTubeServiceError(
            JobErrorCode.PIPELINE_FAILURE,
            f"FFmpeg failed while rendering clip {clip_id}: {proc.stderr[-350:]}",
        )

    return ClipResult(
        id=clip_id,
        title=moment.title,
        description=moment.description,
        hashtags=moment.hashtags,
        score=moment.score,
        start=moment.start,
        end=moment.end,
        duration=round(duration, 2),
        language=language,
        preview_url="",
        download_url="",
    )


def create_clips_zip(clip_files: list[Path], output_zip_path: Path) -> Path:
    with zipfile.ZipFile(output_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in clip_files:
            if path.exists():
                zf.write(path, arcname=path.name)
    return output_zip_path
