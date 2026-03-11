"""
Vercel serverless function: /api/transcript?video_id=xxx
"""
from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import json
import re


def extract_video_id(url_or_id: str) -> str | None:
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
        s
    )
    return m.group(1) if m else None


def get_transcript(video_id: str) -> dict:
    from youtube_transcript_api import YouTubeTranscriptApi
    from youtube_transcript_api._errors import TranscriptsDisabled, NoTranscriptFound, VideoUnavailable

    try:
        transcript_list = YouTubeTranscriptApi.get_transcript(video_id)
        text = " ".join(item["text"].strip() for item in transcript_list if item.get("text"))
        return {"video_id": video_id, "transcript": text}
    except TranscriptsDisabled:
        return {"error": "Transcripts disabled for this video", "status": 404}
    except NoTranscriptFound:
        return {"error": "No transcript found", "status": 404}
    except VideoUnavailable:
        return {"error": "Video unavailable", "status": 404}
    except Exception as e:
        return {"error": str(e), "status": 500}


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        video_id = params.get("video_id", [None])[0] or params.get("videoId", [None])[0]
        url_param = params.get("url", [None])[0]

        vid = video_id or extract_video_id(url_param) if url_param else None
        if not vid:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Provide url= or video_id="}).encode())
            return

        result = get_transcript(vid)
        status = result.pop("status", 200)
        if "error" in result:
            self.send_response(status)
        else:
            self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(result).encode())

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()
