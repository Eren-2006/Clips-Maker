from __future__ import annotations

from enum import Enum
from typing import Literal, Optional
from pydantic import BaseModel, Field


class PipelineStep(str, Enum):
    DOWNLOADING = "DOWNLOADING"
    TRANSCRIBING = "TRANSCRIBING"
    FINDING_MOMENTS = "FINDING_MOMENTS"
    EDITING_CLIPS = "EDITING_CLIPS"
    DONE = "DONE"
    ERROR = "ERROR"


class JobErrorCode(str, Enum):
    INVALID_URL = "INVALID_URL"
    PRIVATE_VIDEO = "PRIVATE_VIDEO"
    VIDEO_TOO_LONG = "VIDEO_TOO_LONG"
    API_FAILURE = "API_FAILURE"
    PIPELINE_FAILURE = "PIPELINE_FAILURE"


class WordTimestamp(BaseModel):
    word: str
    start: float
    end: float


class TranscriptSegment(BaseModel):
    start: float
    end: float
    text: str


class ClipMoment(BaseModel):
    start: float = Field(..., ge=0.0)
    end: float = Field(..., gt=0.0)
    title: str = Field(..., min_length=1, max_length=140)
    description: str = ""
    hashtags: list[str] = Field(default_factory=list)
    score: int = Field(default=75, ge=1, le=100)


class ClipResult(BaseModel):
    id: str
    title: str
    description: str
    hashtags: list[str]
    score: int
    start: float
    end: float
    duration: float
    language: str
    preview_url: str
    download_url: str


class CreateJobRequest(BaseModel):
    youtube_url: str
    mode: Literal["client_ai", "server_ai"] = "server_ai"
    target_language: Literal["auto", "en", "hi", "bn"] = "auto"


class CreateJobResponse(BaseModel):
    job_id: str
    queue_position: int = 0


class SubmitMomentsRequest(BaseModel):
    moments: list[ClipMoment] = Field(..., min_length=1, max_length=12)


class JobStatusResponse(BaseModel):
    job_id: str
    status: Literal["queued", "running", "awaiting_moments", "completed", "failed"]
    step: PipelineStep
    progress_percent: int = Field(default=0, ge=0, le=100)
    status_message: str = ""
    queue_position: int = 0
    video_title: Optional[str] = None
    video_duration_seconds: float = 0.0
    detected_language: Optional[str] = None
    transcript_segments: list[TranscriptSegment] = Field(default_factory=list)
    clips: list[ClipResult] = Field(default_factory=list)
    zip_url: Optional[str] = None
    error_code: Optional[JobErrorCode] = None
    error_message: Optional[str] = None
