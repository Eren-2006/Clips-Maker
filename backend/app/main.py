from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse

from app.config import settings
from app.models import CreateJobRequest, CreateJobResponse, JobStatusResponse, SubmitMomentsRequest
from app.services.cleanup import purge_expired_outputs
from app.services.job_queue import SingleWorkerJobManager
from app.services.youtube import YouTubeServiceError

job_manager = SingleWorkerJobManager(output_dir=settings.output_dir)


async def _cleanup_loop() -> None:
    while True:
        purge_expired_outputs(settings.output_dir, retention_hours=settings.retention_hours)
        await asyncio.sleep(3600)


@asynccontextmanager
async def lifespan(_: FastAPI):
    purge_expired_outputs(settings.output_dir, retention_hours=settings.retention_hours)
    job_manager.start()
    cleanup_task = asyncio.create_task(_cleanup_loop())
    try:
        yield
    finally:
        cleanup_task.cancel()
        await job_manager.stop()


app = FastAPI(title="ClipMaster Backend", version="1.0.0", lifespan=lifespan)

origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()] or ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "service": "clipmaster-backend",
        "active_job_id": job_manager.active_job_id,
        "queued_jobs": job_manager.queue.qsize(),
    }


@app.post("/api/analyze", response_model=CreateJobResponse, status_code=202)
@app.post("/api/jobs", response_model=CreateJobResponse, status_code=202)
async def create_job(request: CreateJobRequest) -> CreateJobResponse:
    try:
        job_id, queue_pos = job_manager.enqueue_job(
            request.youtube_url,
            mode=request.mode,
            target_language=request.target_language,
        )
        return CreateJobResponse(job_id=job_id, queue_position=queue_pos)
    except YouTubeServiceError as exc:
        return JSONResponse(
            status_code=400,
            content={"error_code": exc.code.value, "message": exc.message, "detail": exc.message},
        )


@app.get("/api/jobs/{job_id}", response_model=JobStatusResponse)
async def get_job_status(job_id: str) -> JobStatusResponse:
    try:
        return job_manager.get_status(job_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Job not found or expired.") from exc


@app.post("/api/jobs/{job_id}/moments", response_model=JobStatusResponse)
async def submit_moments(job_id: str, request: SubmitMomentsRequest) -> JobStatusResponse:
    try:
        return job_manager.submit_client_moments(job_id, request.moments)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Job not found or expired.") from exc


@app.get("/api/jobs/{job_id}/clips/{clip_id}")
async def get_clip_file(job_id: str, clip_id: str, download: int = Query(default=0)):
    clip_path = (settings.output_dir / job_id / "clips" / f"{clip_id}.mp4").resolve()
    if not clip_path.exists():
        raise HTTPException(status_code=404, detail="Clip file not found or expired.")
    headers = {}
    if download:
        headers["Content-Disposition"] = f'attachment; filename="{clip_id}.mp4"'
    return FileResponse(path=clip_path, media_type="video/mp4", headers=headers)


@app.get("/api/jobs/{job_id}/zip")
async def get_clips_zip(job_id: str):
    zip_path = (settings.output_dir / job_id / f"clipmaster_{job_id}_clips.zip").resolve()
    if not zip_path.exists():
        raise HTTPException(status_code=404, detail="ZIP archive not found or expired.")
    return FileResponse(
        path=zip_path,
        media_type="application/zip",
        filename=f"clipmaster_{job_id}_clips.zip",
    )


@app.get("/cliping", response_class=HTMLResponse)
async def cliping_web_page() -> HTMLResponse:
    html = """<!doctype html>
<html lang="en" class="dark">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>ClipMaster — /cliping</title>
  <style>
    :root { --bg:#0a0b0e; --card:#14161d; --border:#242836; --yellow:#facc15; --text:#f8fafc; --muted:#94a3b8; }
    * { box-sizing:border-box; }
    body { margin:0; font-family:Inter,system-ui,sans-serif; background:var(--bg); color:var(--text); }
    .wrap { max-width:1100px; margin:0 auto; padding:32px 16px 64px; }
    h1 { font-size:2rem; margin:0 0 8px; }
    p.sub { color:var(--muted); margin:0 0 24px; }
    .box { display:flex; gap:10px; background:var(--card); border:1px solid var(--border); padding:12px; border-radius:16px; flex-wrap:wrap; }
    input, select { background:#1c1f2a; color:var(--text); border:1px solid var(--border); border-radius:12px; padding:14px; font-size:15px; }
    input { flex:1; min-width:220px; }
    button { background:var(--yellow); color:#0a0b0e; border:0; border-radius:12px; padding:14px 22px; font-weight:700; cursor:pointer; }
    button.sec { background:#1c1f2a; color:var(--text); border:1px solid var(--border); }
    .prog { margin-top:20px; background:var(--card); border:1px solid var(--border); border-radius:16px; padding:18px; display:none; }
    .bar { height:8px; background:#1c1f2a; border-radius:999px; overflow:hidden; margin:12px 0; }
    .fill { height:100%; width:0%; background:var(--yellow); transition:width .3s ease; }
    .steps { display:flex; gap:8px; flex-wrap:wrap; font-size:12px; color:var(--muted); }
    .step.active { color:var(--yellow); font-weight:700; }
    .grid { margin-top:24px; display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:18px; }
    .card { background:var(--card); border:1px solid var(--border); border-radius:18px; overflow:hidden; display:flex; flex-direction:column; }
    video { width:100%; aspect-ratio:9/16; background:#000; object-fit:cover; }
    .meta { padding:14px; display:flex; flex-direction:column; gap:8px; }
    .score { display:inline-block; background:#332b08; color:var(--yellow); padding:3px 9px; border-radius:999px; font-size:12px; font-weight:700; width:max-content; }
    .actions { display:flex; gap:8px; margin-top:6px; }
    .err { margin-top:16px; background:#2a1215; border:1px solid #f87171; color:#fecaca; padding:14px; border-radius:12px; display:none; }
  </style>
</head>
<body>
  <div class="wrap">
    <h1>ClipMaster</h1>
    <p class="sub">Turn long YouTube videos into ready-to-post 9:16 short clips with animated English, Hindi &amp; Bengali captions.</p>
    <div class="box">
      <input id="url" type="url" placeholder="https://www.youtube.com/watch?v=..." />
      <select id="lang" aria-label="Caption Language">
        <option value="auto" selected>Auto-detect</option>
        <option value="en">English</option>
        <option value="hi">Hindi (हिन्दी)</option>
        <option value="bn">Bengali (বাংলা)</option>
      </select>
      <button id="go" onclick="startJob()">Generate Clips</button>
    </div>
    <div id="err" class="err"></div>
    <div id="prog" class="prog">
      <div id="statusMsg">Starting...</div>
      <div class="bar"><div id="fill" class="fill"></div></div>
      <div class="steps">
        <span id="s-DOWNLOADING" class="step">Downloading</span> •
        <span id="s-TRANSCRIBING" class="step">Transcribing</span> •
        <span id="s-FINDING_MOMENTS" class="step">Finding best moments</span> •
        <span id="s-EDITING_CLIPS" class="step">Editing clips</span> •
        <span id="s-DONE" class="step">Done</span>
      </div>
    </div>
    <div id="zipWrap" style="margin-top:18px;display:none;"><a id="zipLink" href="#"><button>Download all (ZIP)</button></a></div>
    <div id="grid" class="grid"></div>
  </div>
  <script>
    async function startJob() {
      const url = document.getElementById('url').value.trim();
      const targetLanguage = document.getElementById('lang').value;
      const err = document.getElementById('err');
      err.style.display = 'none';
      document.getElementById('grid').innerHTML = '';
      document.getElementById('zipWrap').style.display = 'none';
      const res = await fetch('/api/jobs', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({youtube_url:url, mode:'server_ai', target_language:targetLanguage})});
      const data = await res.json();
      if (!res.ok) { err.textContent = data.message || data.detail || 'Invalid URL'; err.style.display='block'; return; }
      document.getElementById('prog').style.display = 'block';
      poll(data.job_id);
    }
    async function poll(id) {
      const res = await fetch('/api/jobs/' + id);
      const st = await res.json();
      document.getElementById('statusMsg').textContent = st.status_message;
      document.getElementById('fill').style.width = st.progress_percent + '%';
      document.querySelectorAll('.step').forEach(el => el.classList.remove('active'));
      const cur = document.getElementById('s-' + st.step);
      if (cur) cur.classList.add('active');
      if (st.status === 'failed') {
        const err = document.getElementById('err');
        err.textContent = st.error_message || 'Processing failed';
        err.style.display = 'block';
        return;
      }
      if (st.status === 'completed') {
        if (st.zip_url) { document.getElementById('zipLink').href = st.zip_url; document.getElementById('zipWrap').style.display='block'; }
        const grid = document.getElementById('grid');
        st.clips.forEach(c => {
          const copyText = [c.title, c.description, (c.hashtags||[]).join(' ')].filter(Boolean).join('\\n\\n');
          const el = document.createElement('div');
          el.className = 'card';
          el.innerHTML = `<video controls playsinline src="${c.preview_url}"></video>
            <div class="meta">
              <span class="score">Viral ${c.score}/100</span>
              <strong>${c.title}</strong>
              <small style="color:#94a3b8">${c.description}</small>
              <small style="color:#38bdf8">${(c.hashtags||[]).join(' ')}</small>
              <div class="actions">
                <a href="${c.download_url}" style="flex:1"><button style="width:100%">Download</button></a>
                <button class="sec" onclick='navigator.clipboard.writeText(${JSON.stringify(copyText)})'>Copy Title/Caption</button>
              </div>
            </div>`;
          grid.appendChild(el);
        });
        return;
      }
      setTimeout(() => poll(id), 1200);
    }
  </script>
</body>
</html>"""
    return HTMLResponse(content=html)
