"""
StudyMind Backend - Unified API for Railway / any Python host.
Endpoints: /transcript, /whisper, /gemini_youtube, /audio, /health
"""
import json
import os
import re
import base64
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="StudyMind Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def extract_video_id(url_or_id: str) -> Optional[str]:
    if not url_or_id or not url_or_id.strip():
        return None
    s = url_or_id.strip()
    if len(s) == 11 and re.match(r"^[a-zA-Z0-9_-]+$", s):
        return s
    if s.startswith("ps://"):
        s = "https" + s
    elif not s.startswith("http"):
        s = "https://" + s
    m = re.search(
        r"(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/)([a-zA-Z0-9_-]{11})",
        s,
    )
    return m.group(1) if m else None


# ----- /transcript -----
def _to_text(val) -> str:
    if isinstance(val, str):
        return val
    if isinstance(val, list):
        return " ".join(
            s.get("text", "") if isinstance(s, dict) else str(s) for s in val
        )
    return ""


def _fetch_from_transcript_io(video_id: str, token: str) -> dict:
    import urllib.request

    body = json.dumps({"ids": [video_id]}).encode()
    req = urllib.request.Request(
        "https://www.youtube-transcript.io/api/transcripts",
        data=body,
        headers={
            "Authorization": f"Basic {token}",
            "Content-Type": "application/json",
            "User-Agent": "StudyMind/1.0",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
            if isinstance(data, list) and data:
                obj = data[0]
                text = _to_text(obj.get("transcript") or obj.get("text"))
                if text:
                    return {"transcript": text}
            if isinstance(data, dict):
                if data.get("transcript"):
                    return {"transcript": _to_text(data["transcript"])}
                if data.get("transcripts") and data["transcripts"]:
                    obj = data["transcripts"][0]
                    text = _to_text(obj.get("transcript") or obj.get("text"))
                    if text:
                        return {"transcript": text}
    except Exception as e:
        return {"error": str(e)}
    return {}


@app.get("/transcript")
def get_transcript(url: Optional[str] = None, video_id: Optional[str] = None):
    vid = (video_id and video_id.strip()) or (
        extract_video_id(url) if url else None
    )
    if not vid:
        raise HTTPException(400, "Provide url= or video_id=")

    token = os.environ.get("TRANSCRIPT_API_TOKEN", "").strip()
    if token:
        r = _fetch_from_transcript_io(vid, token)
        if r.get("transcript"):
            return {"video_id": vid, "transcript": r["transcript"]}
        if r.get("error"):
            status = 429 if "429" in r["error"] or "too many" in r["error"].lower() else 500
            raise HTTPException(status, f"Transcript.io: {r['error']}")

    from youtube_transcript_api import YouTubeTranscriptApi

    try:
        fetched = YouTubeTranscriptApi().fetch(vid)
        # FetchedTranscript is iterable; each item has .text or is dict with "text"
        parts = []
        for s in fetched:
            t = getattr(s, "text", None) or (s.get("text") if isinstance(s, dict) else None)
            if t:
                parts.append(str(t).strip())
        text = " ".join(parts)
        return {"video_id": vid, "transcript": text}
    except Exception as e:
        err_msg = str(e)
        if "429" in err_msg or "too many requests" in err_msg.lower():
            raise HTTPException(429, "Rate limited. Please wait a few minutes.")
        if "disabled" in err_msg.lower() or "not found" in err_msg.lower():
            raise HTTPException(404, err_msg)
        raise HTTPException(500, err_msg)


# ----- /whisper -----
class WhisperRequest(BaseModel):
    audio: str
    filename: Optional[str] = "audio.m4a"
    mimeType: Optional[str] = "audio/mp4"


@app.post("/whisper")
def whisper_post(req: WhisperRequest):
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        raise HTTPException(500, "OPENAI_API_KEY not configured")

    try:
        audio_bytes = base64.b64decode(req.audio)
    except Exception as e:
        raise HTTPException(400, f"Invalid base64: {e}")

    if len(audio_bytes) > 25 * 1024 * 1024:
        raise HTTPException(400, "Audio too large (max 25MB)")

    import requests

    try:
        r = requests.post(
            "https://api.openai.com/v1/audio/transcriptions",
            headers={"Authorization": f"Bearer {key}"},
            files={"file": (req.filename, audio_bytes, req.mimeType)},
            data={"model": "whisper-1"},
            timeout=300,
        )
        r.raise_for_status()
        return {"text": r.json().get("text", "")}
    except requests.RequestException as e:
        raise HTTPException(500, str(e))


# ----- /gemini_youtube -----
class GeminiYoutubeRequest(BaseModel):
    url: str
    title: Optional[str] = None


@app.post("/gemini_youtube")
def gemini_youtube_post(req: GeminiYoutubeRequest):
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        raise HTTPException(500, "GEMINI_API_KEY not configured")

    prompt = (
        "You are StudyMind AI. Analyze this YouTube video and create EXAM-FOCUSED structured notes. "
        "Output ONLY valid JSON (use \\n for newlines), no markdown:\n"
        '{"keyDefinitions":"...","coreConcepts":"...","importantFormulas":"...","commonPitfalls":"...","quickReview":"..."}\n'
        "MUST populate ALL 5 sections. Use • for main topics and - for sub-points."
    )

    import urllib.request

    body = {
        "contents": [{
            "parts": [
                {"fileData": {"fileUri": req.url}},
                {"text": f"Video: {req.title or 'YouTube'}\n\n{prompt}"},
            ]
        }],
        "generationConfig": {"temperature": 0.4, "maxOutputTokens": 8192},
    }

    req_obj = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={key}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req_obj, timeout=120) as resp:
            data = json.loads(resp.read().decode())
            cands = data.get("candidates", [])
            if not cands:
                raise HTTPException(500, "No candidates")
            parts = cands[0].get("content", {}).get("parts", [])
            if not parts:
                raise HTTPException(500, "No parts")
            return {"notes": parts[0].get("text", "")}
    except urllib.error.HTTPError as e:
        raise HTTPException(e.code, e.read().decode() if e.fp else str(e))
    except Exception as e:
        raise HTTPException(500, str(e))


# ----- /audio -----
@app.get("/audio")
def get_audio(video_id: Optional[str] = None, url: Optional[str] = None):
    vid = (video_id and video_id.strip()) or (
        extract_video_id(url) if url else None
    )
    if not vid:
        raise HTTPException(400, "Provide video_id= or url=")

    try:
        from pytubefix import YouTube

        yt = YouTube(f"https://www.youtube.com/watch?v={vid}")
        stream = yt.streams.get_audio_only()
        if not stream:
            raise HTTPException(404, "No audio stream")
        audio_url = stream.url
        if not audio_url:
            raise HTTPException(404, "No audio URL")
        return {"video_id": vid, "url": audio_url}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, str(e))


# ----- /health -----
@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8000)))
