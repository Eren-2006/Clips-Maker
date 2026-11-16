from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

# Ensure pydantic is optional if running stdlib-only check, or import services directly
try:
    from app.models import ClipMoment, WordTimestamp
    from app.services.cleanup import purge_expired_outputs
    from app.services.moment_selector import _build_selection_prompt, parse_moments_payload
    from app.services.transcriber import (
        extract_audio_wav,
        group_words_into_sentences,
        resolve_whisper_language_hint,
    )
    from app.services.video_editor import (
        build_ass_captions,
        create_clips_zip,
        probe_video_dimensions,
        render_short_clip,
    )
    from app.services.youtube import YouTubeServiceError, validate_youtube_url

    HAS_APP_DEPS = True
except ImportError:
    HAS_APP_DEPS = False


@unittest.skipUnless(HAS_APP_DEPS, "FastAPI/Pydantic dependencies not installed in this Python env")
class ClipMasterPipelineTests(unittest.TestCase):
    def test_youtube_url_validation(self) -> None:
        self.assertEqual(
            validate_youtube_url("https://youtu.be/dQw4w9WgXcQ"),
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
        self.assertEqual(
            validate_youtube_url("https://www.youtube.com/shorts/aqz-KE-bpKQ"),
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        )
        with self.assertRaises(YouTubeServiceError):
            validate_youtube_url("https://vimeo.com/123456")

    def test_sentence_grouping_and_bengali_ass_captions(self) -> None:
        words = [
            WordTimestamp(word="স্বাগতম", start=1.0, end=1.4),
            WordTimestamp(word="বন্ধুরা", start=1.4, end=1.9),
            WordTimestamp(word="আজকে", start=1.9, end=2.3),
            WordTimestamp(word="শিখব।", start=2.3, end=2.8),
        ]
        segments = group_words_into_sentences(words)
        self.assertEqual(len(segments), 1)
        self.assertIn("স্বাগতম", segments[0].text)

        with tempfile.TemporaryDirectory() as tmp:
            ass_path = Path(tmp) / "bn.ass"
            build_ass_captions(words, clip_start=0.5, clip_end=4.0, language="bn", output_ass_path=ass_path)
            content = ass_path.read_text(encoding="utf-8")
            self.assertIn("Noto Sans Bengali", content)
            self.assertIn("\\c&H0015CCFA&", content)

    def test_moment_json_parsing(self) -> None:
        raw = '```json\n{"moments":[{"start":5.0,"end":32.5,"title":"Great Hook","description":"Insight","hashtags":["Shorts"],"score":91}]}\n```'
        moments = parse_moments_payload(raw, max_duration=120.0)
        self.assertEqual(len(moments), 1)
        self.assertEqual(moments[0].score, 91)
        self.assertEqual(moments[0].hashtags, ["#Shorts"])

    def test_target_language_whisper_hint_and_prompt(self) -> None:
        self.assertIsNone(resolve_whisper_language_hint("auto"))
        self.assertIsNone(resolve_whisper_language_hint(None))
        self.assertEqual(resolve_whisper_language_hint("en"), "en")
        self.assertEqual(resolve_whisper_language_hint("HI"), "hi")
        self.assertEqual(resolve_whisper_language_hint("bn"), "bn")

        prompt = _build_selection_prompt(
            video_title="Sample",
            language="en",
            duration_seconds=90.0,
            segments=[],
            target_language="hi",
        )
        self.assertIn("Hindi (हिन्दी) (hi)", prompt)
        self.assertIn("strictly in Hindi (हिन्दी)", prompt)

    def test_full_ffmpeg_render_and_24h_cleanup_with_sample_video(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            sample_mp4 = work / "sample_16_9.mp4"
            # Generate real 6-second 1280x720 video + 440Hz audio using ffmpeg
            gen_cmd = [
                "ffmpeg", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=6:size=1280x720:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=6",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                str(sample_mp4),
            ]
            proc = subprocess.run(gen_cmd, capture_output=True, text=True)
            self.assertEqual(proc.returncode, 0, proc.stderr)

            wav_path = work / "audio.wav"
            extract_audio_wav(sample_mp4, wav_path)
            self.assertTrue(wav_path.exists() and wav_path.stat().st_size > 0)

            words = [
                WordTimestamp(word="Welcome", start=0.5, end=1.1),
                WordTimestamp(word="to", start=1.1, end=1.4),
                WordTimestamp(word="ClipMaster", start=1.4, end=2.2),
                WordTimestamp(word="Shorts!", start=2.2, end=3.0),
            ]
            moment = ClipMoment(
                start=0.5,
                end=4.5,
                title="Sample Short Clip",
                description="Rendered via FFmpeg pipeline",
                hashtags=["#Shorts"],
                score=88,
            )
            out_clip = work / "clip_01.mp4"
            result = render_short_clip(
                source_video=sample_mp4,
                moment=moment,
                words=words,
                language="en",
                output_mp4=out_clip,
                work_dir=work,
                clip_id="clip_01",
            )
            self.assertTrue(out_clip.exists())
            dims = probe_video_dimensions(out_clip)
            self.assertEqual((dims.width, dims.height), (1080, 1920))
            self.assertEqual(result.score, 88)

            zip_path = work / "bundle.zip"
            create_clips_zip([out_clip], zip_path)
            self.assertTrue(zip_path.exists())
            with zipfile.ZipFile(zip_path, "r") as zf:
                self.assertIn("clip_01.mp4", zf.namelist())

            # Test 24h cleanup reaper
            expired_dir = work / "outputs" / "old_job"
            fresh_dir = work / "outputs" / "fresh_job"
            expired_dir.mkdir(parents=True)
            fresh_dir.mkdir(parents=True)
            now = time.time()
            old_ts = now - (25 * 3600)
            os.utime(expired_dir, (old_ts, old_ts))

            removed = purge_expired_outputs(work / "outputs", retention_hours=24, now_ts=now)
            self.assertEqual(removed, 1)
            self.assertFalse(expired_dir.exists())
            self.assertTrue(fresh_dir.exists())


if __name__ == "__main__":
    unittest.main()
