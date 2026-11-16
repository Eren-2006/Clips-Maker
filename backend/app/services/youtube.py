from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional
from urllib.parse import parse_qs, urlparse

from app.config import settings
from app.models import JobErrorCode


YOUTUBE_HOSTS = {
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtu.be",
    "www.youtu.be",
}
VIDEO_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{11}$")


class YouTubeServiceError(Exception):
    def __init__(self, code: JobErrorCode, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class VideoMetadata:
    video_id: str
    title: str
    duration_seconds: float
    webpage_url: str


def validate_youtube_url(raw_url: str) -> str:
    trimmed = raw_url.strip()
    if not trimmed:
        raise YouTubeServiceError(JobErrorCode.INVALID_URL, "Please enter a YouTube video URL.")

    candidate = trimmed if trimmed.startswith(("http://", "https://")) else f"https://{trimmed}"
    parsed = urlparse(candidate)
    host = (parsed.hostname or "").lower()
    if host not in YOUTUBE_HOSTS:
        raise YouTubeServiceError(
            JobErrorCode.INVALID_URL,
            "Only valid YouTube URLs (youtube.com or youtu.be) are supported.",
        )

    path_parts = [part for part in parsed.path.strip("/").split("/") if part]
    video_id: Optional[str] = None
    if host.endswith("youtu.be"):
        video_id = path_parts[0] if path_parts else None
    elif path_parts:
        head = path_parts[0]
        if head == "watch":
            video_id = parse_qs(parsed.query).get("v", [None])[0]
        elif head in {"shorts", "live", "embed", "v"} and len(path_parts) >= 2:
            video_id = path_parts[1]
        else:
            video_id = parse_qs(parsed.query).get("v", [None])[0]

    if not video_id or not VIDEO_ID_PATTERN.match(video_id):
        raise YouTubeServiceError(
            JobErrorCode.INVALID_URL,
            "Could not extract a valid 11-character YouTube video ID from that link.",
        )

    return f"https://www.youtube.com/watch?v={video_id}"


def _classify_yt_dlp_error(exc: Exception) -> YouTubeServiceError:
    text = str(exc).lower()
    if "private video" in text or "sign in to confirm" in text or "members-only" in text:
        return YouTubeServiceError(
            JobErrorCode.PRIVATE_VIDEO,
            "This YouTube video is private, members-only, or requires sign-in.",
        )
    if "video unavailable" in text or "has been removed" in text or "copyright" in text:
        return YouTubeServiceError(
            JobErrorCode.PRIVATE_VIDEO,
            "This YouTube video is unavailable or has been removed.",
        )
    if "not a valid url" in text or "unsupported url" in text:
        return YouTubeServiceError(
            JobErrorCode.INVALID_URL,
            "That URL is not a valid downloadable YouTube video.",
        )
    return YouTubeServiceError(
        JobErrorCode.PIPELINE_FAILURE,
        f"Failed to download video from YouTube: {exc}",
    )


def fetch_and_download_video(
    youtube_url: str,
    job_dir: Path,
    progress_callback: Optional[Callable[[int, str], None]] = None,
) -> tuple[VideoMetadata, Path]:
    normalized_url = validate_youtube_url(youtube_url)

    try:
        import yt_dlp
    except ImportError as exc:
        raise YouTubeServiceError(
            JobErrorCode.PIPELINE_FAILURE,
            "yt-dlp is not installed on the backend server.",
        ) from exc

    job_dir.mkdir(parents=True, exist_ok=True)
    probe_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "extract_flat": False,
    }

    try:
        with yt_dlp.YoutubeDL(probe_opts) as ydl:
            info = ydl.extract_info(normalized_url, download=False)
    except Exception as exc:
        raise _classify_yt_dlp_error(exc) from exc

    if not info:
        raise YouTubeServiceError(JobErrorCode.PRIVATE_VIDEO, "Unable to retrieve video metadata.")

    if info.get("is_live"):
        raise YouTubeServiceError(
            JobErrorCode.INVALID_URL,
            "Active livestreams cannot be clipped until the stream finishes.",
        )

    duration = float(info.get("duration") or 0.0)
    if duration > settings.max_video_seconds:
        hours = settings.max_video_seconds / 3600
        raise YouTubeServiceError(
            JobErrorCode.VIDEO_TOO_LONG,
            f"Video is {duration / 60:.0f} minutes long, exceeding the {hours:.0f}-hour limit.",
        )

    title = str(info.get("title") or "YouTube Video").strip()
    video_id = str(info.get("id") or "video")

    def _hook(data: dict) -> None:
        if not progress_callback:
            return
        if data.get("status") == "downloading":
            total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
            downloaded = data.get("downloaded_bytes") or 0
            pct = int(5 + (downloaded / total) * 20) if total > 0 else 15
            progress_callback(min(25, pct), "Downloading video (up to 1080p)...")
        elif data.get("status") == "finished":
            progress_callback(26, "Download complete, preparing audio...")

    output_template = str(job_dir / "source.%(ext)s")
    download_opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]/best",
        "merge_output_format": "mp4",
        "outtmpl": output_template,
        "progress_hooks": [_hook],
    }

    try:
        with yt_dlp.YoutubeDL(download_opts) as ydl:
            ydl.download([normalized_url])
    except Exception as exc:
        raise _classify_yt_dlp_error(exc) from exc

    mp4_path = job_dir / "source.mp4"
    if not mp4_path.exists():
        candidates = sorted(job_dir.glob("source.*"))
        if not candidates:
            raise YouTubeServiceError(
                JobErrorCode.PIPELINE_FAILURE,
                "yt-dlp finished without producing a source video file.",
            )
        mp4_path = candidates[0]

    metadata = VideoMetadata(
        video_id=video_id,
        title=title,
        duration_seconds=duration,
        webpage_url=normalized_url,
    )
    return metadata, mp4_path
