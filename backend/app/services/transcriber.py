from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Optional

from app.config import settings
from app.models import JobErrorCode, TranscriptSegment, WordTimestamp
from app.services.youtube import YouTubeServiceError

SUPPORTED_LANGUAGES = {"en", "hi", "bn"}
_WHISPER_MODEL_CACHE: dict[str, object] = {}


def extract_audio_wav(video_path: Path, wav_path: Path) -> Path:
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        str(video_path),
        "-vn",
        "-acodec",
        "pcm_s16le",
        "-ar",
        "16000",
        "-ac",
        "1",
        str(wav_path),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0 or not wav_path.exists():
        raise YouTubeServiceError(
            JobErrorCode.PIPELINE_FAILURE,
            f"FFmpeg audio extraction failed: {proc.stderr[-300:]}",
        )
    return wav_path


def _load_whisper_model(model_name: str):
    if model_name not in _WHISPER_MODEL_CACHE:
        import whisper
        _WHISPER_MODEL_CACHE[model_name] = whisper.load_model(model_name)
    return _WHISPER_MODEL_CACHE[model_name]


def _normalize_language(raw_lang: Optional[str], text_sample: str) -> str:
    if raw_lang and raw_lang.lower() in SUPPORTED_LANGUAGES:
        return raw_lang.lower()
    # Fallback script detection for Bengali / Devanagari
    if any("\u0980" <= ch <= "\u09ff" for ch in text_sample):
        return "bn"
    if any("\u0900" <= ch <= "\u097f" for ch in text_sample):
        return "hi"
    return "en"


def group_words_into_sentences(
    words: list[WordTimestamp],
    raw_segments: Optional[list[dict]] = None,
) -> list[TranscriptSegment]:
    if words:
        sentences: list[TranscriptSegment] = []
        bucket: list[WordTimestamp] = []
        for w in words:
            bucket.append(w)
            token = w.word.strip()
            ends_sentence = token.endswith((".", "?", "!", "।"))
            duration = bucket[-1].end - bucket[0].start
            if ends_sentence or duration >= 12.0 or len(bucket) >= 24:
                sentences.append(
                    TranscriptSegment(
                        start=round(bucket[0].start, 2),
                        end=round(bucket[-1].end, 2),
                        text=" ".join(item.word.strip() for item in bucket).strip(),
                    )
                )
                bucket = []
        if bucket:
            sentences.append(
                TranscriptSegment(
                    start=round(bucket[0].start, 2),
                    end=round(bucket[-1].end, 2),
                    text=" ".join(item.word.strip() for item in bucket).strip(),
                )
            )
        return [s for s in sentences if s.text]

    if raw_segments:
        return [
            TranscriptSegment(
                start=round(float(seg.get("start", 0.0)), 2),
                end=round(float(seg.get("end", 0.0)), 2),
                text=str(seg.get("text", "")).strip(),
            )
            for seg in raw_segments
            if str(seg.get("text", "")).strip()
        ]
    return []


def resolve_whisper_language_hint(target_language: Optional[str]) -> Optional[str]:
    if not target_language:
        return None
    normalized = target_language.strip().lower()
    if normalized in SUPPORTED_LANGUAGES:
        return normalized
    return None


def transcribe_audio_whisper(
    wav_path: Path,
    target_language: str = "auto",
) -> tuple[str, list[WordTimestamp], list[TranscriptSegment]]:
    try:
        model = _load_whisper_model(settings.whisper_model)
    except Exception as exc:
        raise YouTubeServiceError(
            JobErrorCode.PIPELINE_FAILURE,
            f"Failed to load OpenAI Whisper model '{settings.whisper_model}': {exc}",
        ) from exc

    language_hint = resolve_whisper_language_hint(target_language)
    transcribe_kwargs: dict[str, object] = {
        "word_timestamps": True,
        "task": "transcribe",
        "fp16": False,
    }
    if language_hint:
        transcribe_kwargs["language"] = language_hint

    result = model.transcribe(str(wav_path), **transcribe_kwargs)

    words: list[WordTimestamp] = []
    raw_segments = result.get("segments") or []
    full_text_parts: list[str] = []

    for seg in raw_segments:
        full_text_parts.append(str(seg.get("text", "")))
        for w in seg.get("words") or []:
            word_text = str(w.get("word", "")).strip()
            if not word_text:
                continue
            start = float(w.get("start", seg.get("start", 0.0)))
            end = float(w.get("end", start + 0.25))
            if end <= start:
                end = start + 0.15
            words.append(WordTimestamp(word=word_text, start=start, end=end))

    effective_lang = language_hint or _normalize_language(
        result.get("language"), " ".join(full_text_parts)
    )
    sentences = group_words_into_sentences(words, raw_segments)
    return effective_lang, words, sentences
