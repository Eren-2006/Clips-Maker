from __future__ import annotations

import json
from typing import Any

from app.config import settings
from app.models import ClipMoment, JobErrorCode, TranscriptSegment
from app.services.youtube import YouTubeServiceError


def _build_selection_prompt(
    video_title: str,
    language: str,
    duration_seconds: float,
    segments: list[TranscriptSegment],
    target_language: str = "auto",
) -> str:
    transcript_lines = "\n".join(
        f"[{seg.start:.2f} -> {seg.end:.2f}] {seg.text}" for seg in segments
    )
    effective_lang = target_language if target_language in {"en", "hi", "bn"} else language
    lang_names = {"en": "English", "hi": "Hindi (हिन्दी)", "bn": "Bengali (বাংলা)"}
    lang_label = lang_names.get(effective_lang, effective_lang)
    lang_rule = (
        f"- Write title, description, and hashtags strictly in {lang_label}."
        if target_language in {"en", "hi", "bn"}
        else "- Write title, description, and hashtags in the speaker's language (English, Hindi, or Bengali)."
    )
    return (
        "You are an expert short-form video editor. Pick the 5 to 10 best viral moments "
        "(hook, emotion, insight, funny, or surprising) from the transcript below.\n"
        f"Title: {video_title}\n"
        f"Target Caption Language: {lang_label} ({effective_lang})\n"
        f"Total Duration: {duration_seconds:.1f}s\n\n"
        "Rules:\n"
        "- Each clip MUST be 20 to 60 seconds long (unless the whole video is under 25s).\n"
        "- Start and end MUST align to complete sentence boundaries from the transcript.\n"
        f"{lang_rule}\n"
        "- Return JSON object {\"moments\": [{\"start\": float, \"end\": float, \"title\": str, "
        "\"description\": str, \"hashtags\": [str], \"score\": int}]}.\n\n"
        f"Transcript:\n{transcript_lines}"
    )


def parse_moments_payload(raw_json: str, max_duration: float) -> list[ClipMoment]:
    cleaned = raw_json.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        cleaned = "\n".join(lines[1:-1] if lines[-1].strip() == "```" else lines[1:]).strip()

    data: Any = json.loads(cleaned)
    items = data.get("moments", []) if isinstance(data, dict) else data
    moments: list[ClipMoment] = []

    for item in items:
        start = max(0.0, float(item.get("start", 0.0)))
        end = min(max_duration if max_duration > 0 else 1e9, float(item.get("end", start + 30.0)))
        if end - start < 5.0:
            continue
        if end - start > 65.0:
            end = start + 60.0
        title = str(item.get("title", "")).strip()
        if not title:
            continue
        hashtags = [
            tag if tag.startswith("#") else f"#{tag}"
            for tag in (str(t).strip() for t in (item.get("hashtags") or []))
            if tag
        ] or ["#Shorts"]
        score = max(1, min(100, int(item.get("score", 75))))
        moments.append(
            ClipMoment(
                start=round(start, 2),
                end=round(end, 2),
                title=title[:140],
                description=str(item.get("description", "")).strip(),
                hashtags=hashtags[:6],
                score=score,
            )
        )

    moments.sort(key=lambda m: m.score, reverse=True)
    return moments[:10]


def select_best_moments_llm(
    video_title: str,
    language: str,
    duration_seconds: float,
    segments: list[TranscriptSegment],
    target_language: str = "auto",
) -> list[ClipMoment]:
    if not segments:
        raise YouTubeServiceError(
            JobErrorCode.API_FAILURE,
            "No speech transcript segments were extracted to analyze.",
        )

    prompt = _build_selection_prompt(
        video_title,
        language,
        duration_seconds,
        segments,
        target_language=target_language,
    )

    if settings.llm_provider == "openai" or (
        not settings.gemini_api_key and settings.openai_api_key
    ):
        if not settings.openai_api_key:
            raise YouTubeServiceError(
                JobErrorCode.API_FAILURE,
                "OPENAI_API_KEY is not configured in backend environment variables.",
            )
        try:
            from openai import OpenAI

            client = OpenAI(api_key=settings.openai_api_key)
            resp = client.chat.completions.create(
                model=settings.openai_model,
                response_format={"type": "json_object"},
                messages=[{"role": "user", "content": prompt}],
                temperature=0.4,
            )
            content = resp.choices[0].message.content or "{}"
            moments = parse_moments_payload(content, duration_seconds)
            if not moments:
                raise YouTubeServiceError(JobErrorCode.API_FAILURE, "LLM returned no valid moments.")
            return moments
        except YouTubeServiceError:
            raise
        except Exception as exc:
            raise YouTubeServiceError(
                JobErrorCode.API_FAILURE,
                f"OpenAI moment selection failed: {exc}",
            ) from exc

    if not settings.gemini_api_key:
        raise YouTubeServiceError(
            JobErrorCode.API_FAILURE,
            "GEMINI_API_KEY is not configured in backend environment variables.",
        )

    try:
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=settings.gemini_api_key)
        resp = client.models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                temperature=0.4,
            ),
        )
        moments = parse_moments_payload(resp.text or "{}", duration_seconds)
        if not moments:
            raise YouTubeServiceError(JobErrorCode.API_FAILURE, "Gemini returned no valid moments.")
        return moments
    except YouTubeServiceError:
        raise
    except Exception as exc:
        raise YouTubeServiceError(
            JobErrorCode.API_FAILURE,
            f"Gemini moment selection failed: {exc}",
        ) from exc
