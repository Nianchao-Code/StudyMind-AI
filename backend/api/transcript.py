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
    # 1. Try youtube-transcript.io if TRANSCRIPT_API_TOKEN is set (Vercel env)
    import os
    token = os.environ.get("TRANSCRIPT_API_TOKEN", "").strip()
    if token:
        r = _fetch_from_transcript_io(video_id, token)
        if r.get("transcript"):
            return {"video_id": video_id, "transcript": r["transcript"]}
        if r.get("error"):
            # transcript.io 返回错误（401/429等），直接返回，不要 fallback 到 YouTube
            err = r["error"]
            status = 429 if "429" in err or "too many" in err.lower() else 500
            return {"error": f"Transcript.io: {err}", "status": status}
        # r 为空 {}：该视频在 transcript.io 无结果，fallback 到 YouTube
    else:
        # Token not set - will use free youtube-transcript-api (YouTube rate limits apply)
        pass

    # 2. Fallback: youtube-transcript-api (1.2.x) - hits YouTube directly, subject to 429
    from youtube_transcript_api import YouTubeTranscriptApi

    try:
        fetched = YouTubeTranscriptApi().fetch(video_id)
        text = " ".join(s.text.strip() for s in fetched if s.text)
        return {"video_id": video_id, "transcript": text}
    except Exception as e:
        err_msg = str(e)
        # "no element found: line 1, column 0" = YouTube returned empty/invalid XML
        if "no element found" in err_msg.lower() or "line 1, column 0" in err_msg:
            return {"error": "YouTube transcript unavailable for this video", "status": 404}
        if "transcript" in err_msg.lower() and ("disabled" in err_msg.lower() or "not found" in err_msg.lower() or "unavailable" in err_msg.lower()):
            return {"error": err_msg, "status": 404}
        # 429 Too Many Requests = rate limited by YouTube
        if "429" in err_msg or "too many requests" in err_msg.lower():
            hint = " Add TRANSCRIPT_API_TOKEN (youtube-transcript.io) in Vercel to use paid API." if not token else ""
            return {"error": "Rate limited. Please wait a few minutes and try again." + hint, "status": 429}
        return {"error": err_msg, "status": 500}


def _to_text(val) -> str:
    if isinstance(val, str):
        return val
    if isinstance(val, list):
        return " ".join(s.get("text", "") if isinstance(s, dict) else str(s) for s in val)
    return ""


def _fetch_from_transcript_io(video_id: str, token: str) -> dict:
    import base64
    import urllib.request

    # Basic auth: token as username, empty password. Some APIs also require X-Telemetry-Id.
    auth = base64.b64encode((token + ":").encode()).decode()
    body = json.dumps({"ids": [video_id]}).encode()
    req = urllib.request.Request(
        "https://www.youtube-transcript.io/api/transcripts",
        data=body,
        headers={
            "Authorization": f"Basic {auth}",
            "Content-Type": "application/json",
            "X-Telemetry-Id": "9821-b",
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
                if data.get("data"):
                    for item in data["data"]:
                        if item.get("video_id") == video_id or item.get("id") == video_id:
                            text = _to_text(item.get("transcript") or item.get("text"))
                            if text:
                                return {"transcript": text}
    except Exception as e:
        return {"error": str(e)}
    return {}


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        video_id = (params.get("video_id") or params.get("videoId") or [None])[0]
        url_param = (params.get("url") or [None])[0]

        vid = (video_id and str(video_id).strip()) or (extract_video_id(str(url_param)) if url_param else None)
        if not vid:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            err = {"error": "Provide url= or video_id=", "debug_path": self.path, "debug_query": parsed.query}
            self.wfile.write(json.dumps(err).encode())
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
