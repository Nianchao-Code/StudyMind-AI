"""
StudyMind Transcript Backend
Fetches YouTube transcripts using youtube-transcript-api (reliable server-side).
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import (
    TranscriptsDisabled,
    NoTranscriptFound,
    VideoUnavailable,
)

app = FastAPI(title="StudyMind Transcript API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
)


def extract_video_id(url: str) -> str | None:
    """Extract video ID from YouTube URL."""
    import re
    if not url or not url.strip():
        return None
    s = url.strip()
    # Fix common typos
    if s.startswith("ps://"):
        s = "https" + s
    elif not s.startswith("http"):
        s = "https://" + s
    m = re.search(
        r"(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/)([a-zA-Z0-9_-]{11})",
        s
    )
    return m.group(1) if m else None


@app.get("/transcript")
def get_transcript(url: str | None = None, video_id: str | None = None):
    """
    Get YouTube transcript.
    Pass either ?url=https://youtube.com/watch?v=xxx or ?video_id=xxx
    """
    vid = video_id or (extract_video_id(url) if url else None)
    if not vid:
        raise HTTPException(
            status_code=400,
            detail="Provide url= or video_id= parameter"
        )

    try:
        transcript_list = YouTubeTranscriptApi.get_transcript(vid)
        text = " ".join(item["text"].strip() for item in transcript_list if item.get("text"))
        return {"video_id": vid, "transcript": text}
    except TranscriptsDisabled:
        raise HTTPException(status_code=404, detail="Transcripts disabled for this video")
    except NoTranscriptFound:
        raise HTTPException(status_code=404, detail="No transcript found")
    except VideoUnavailable:
        raise HTTPException(status_code=404, detail="Video unavailable")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {"status": "ok"}
