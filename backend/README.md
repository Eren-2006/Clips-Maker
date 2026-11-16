# ClipMaster Backend (Python FastAPI)

Companion video-processing pipeline for **ClipMaster** that turns long YouTube videos (up to 2 hours) into ready-to-post 9:16 vertical short clips with face-tracked reframing, animated word-by-word multilingual captions (English, Hindi, Bengali), EBU R128 loudness normalization, and 24-hour auto-cleanup.

## Architecture

- **Single-Worker Job Queue (`app/services/job_queue.py`)**: Processes one video job at a time sequentially via `asyncio.Queue` to keep CPU/memory usage predictable.
- **YouTube Downloader (`app/services/youtube.py`)**: Validates URLs, checks video length (`<= 7200` seconds) and availability before downloading best quality up to `1080p` with `yt-dlp`.
- **Multilingual Transcriber (`app/services/transcriber.py`)**: Extracts 16 kHz mono WAV via `ffmpeg` and runs OpenAI Whisper with `word_timestamps=True`, supporting optional `target_language` hints (`en`, `hi`, `bn`) or automatic language detection (`auto`).
- **Moment Selector (`app/services/moment_selector.py`)**:
  - Supports **Android Hybrid Mode (`client_ai`)**, where the Android app analyzes the transcript using **Gemini via Firebase AI Logic** and submits the selected moments back to `/api/jobs/{job_id}/moments`.
  - Supports **Standalone Web/Server Mode (`server_ai`)**, using `GEMINI_API_KEY` or `OPENAI_API_KEY` from environment variables.
- **9:16 Video Editor (`app/services/video_editor.py`)**:
  - Detects the speaker's face (MediaPipe / OpenCV Haar cascade) and crops smoothly to `1080x1920`, automatically falling back to a Gaussian-blurred 9:16 background fit when no face is detected.
  - Burns in animated word-by-word `.ass` captions (bold font, yellow `#FACC15` active-word highlight, safe margins, max 2 lines, auto-switching to `Noto Sans Bengali` for Bengali script).
  - Applies FFmpeg `loudnorm` audio loudness normalization and exports H.264/AAC MP4 (`+faststart`) plus a ZIP archive.
- **24-Hour Reaper (`app/services/cleanup.py`)**: Automatically removes job folders and files older than 24 hours.

## Prerequisites

1. **Python 3.10+**
2. **FFmpeg** (`ffmpeg` and `ffprobe` on `PATH`)
3. **Noto Sans & Noto Sans Bengali fonts** (for Bengali/Hindi/English captions):
   - Ubuntu/Debian: `sudo apt-get install ffmpeg fonts-noto-core fonts-noto-extra`
   - macOS: `brew install ffmpeg` and install Google Fonts `Noto Sans` + `Noto Sans Bengali`

## Local Setup

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env
# Edit .env and add GEMINI_API_KEY or OPENAI_API_KEY if using standalone web mode (/cliping)

uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Once running:
- **Web Studio**: Open `http://localhost:8000/cliping` in a browser.
- **Android Emulator**: The Android app defaults to `http://10.0.2.2:8000` (reachable from the emulator to host `localhost:8000`).
- **Physical Android Device**: Set the server URL in the Android app's **Server Settings** gear icon to `http://<YOUR_LAN_IP>:8000` or your HTTPS deployment URL.

## Running Tests

```bash
cd backend
python3 -m unittest discover -s tests -v
# or with pytest:
pytest -v
```
