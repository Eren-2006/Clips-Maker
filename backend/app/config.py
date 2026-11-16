from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


@dataclass(frozen=True)
class Settings:
    llm_provider: str = os.getenv("LLM_PROVIDER", "gemini").strip().lower()
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "").strip()
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "").strip()
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip()
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip()
    whisper_model: str = os.getenv("WHISPER_MODEL", "base").strip()
    max_video_seconds: int = int(os.getenv("MAX_VIDEO_SECONDS", "7200"))
    retention_hours: int = int(os.getenv("RETENTION_HOURS", "24"))
    output_dir: Path = Path(os.getenv("OUTPUT_DIR", "./outputs")).resolve()
    port: int = int(os.getenv("PORT", "8000"))
    cors_origins: str = os.getenv("CORS_ORIGINS", "*")


settings = Settings()
