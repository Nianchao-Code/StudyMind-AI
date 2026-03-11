"""
Vercel serverless function: /api/audio?video_id=xxx
Returns direct audio URL for Whisper fallback (uses pytubefix).
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


def get_audio_url(video_id: str) -> dict:
    try:
        from pytubefix import YouTube
        yt = YouTube(f"https://www.youtube.com/watch?v={video_id}")
        stream = yt.streams.get_audio_only()
        if not stream:
            return {"error": "No audio stream", "status": 404}
        audio_url = stream.url
        if not audio_url:
            return {"error": "No audio URL", "status": 404}
        return {"video_id": video_id, "url": audio_url}
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

        result = get_audio_url(vid)
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
