"""
Vercel serverless: POST /api/whisper
Proxies to OpenAI Whisper. Body: {audio: base64, filename, mimeType}. Returns {text}.
Max ~3MB audio (Vercel 4.5MB limit).
"""
from http.server import BaseHTTPRequestHandler
import json
import os
import base64


def do_whisper(audio_b64: str, filename: str, mime_type: str) -> dict:
    import requests

    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        return {"error": "OPENAI_API_KEY not configured", "status": 500}

    try:
        audio_bytes = base64.b64decode(audio_b64)
    except Exception as e:
        return {"error": f"Invalid base64: {e}", "status": 400}

    if len(audio_bytes) > 3 * 1024 * 1024:  # 3MB
        return {"error": "Audio too large (max 3MB)", "status": 400}

    try:
        r = requests.post(
            "https://api.openai.com/v1/audio/transcriptions",
            headers={"Authorization": f"Bearer {key}"},
            files={"file": (filename, audio_bytes, mime_type)},
            data={"model": "whisper-1"},
            timeout=300,
        )
        r.raise_for_status()
        return {"text": r.json().get("text", "")}
    except Exception as e:
        return {"error": str(e), "status": 500}


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length).decode() if length else "{}"
            body = json.loads(raw)
            audio_b64 = body.get("audio", "")
            filename = body.get("filename", "audio.m4a")
            mime_type = body.get("mimeType", "audio/mp4")
            if not audio_b64:
                self._send(400, {"error": "audio required"})
                return
            result = do_whisper(audio_b64, filename, mime_type)
            status = result.pop("status", 200)
            if "error" in result:
                self._send(status, result)
            else:
                self._send(200, result)
        except Exception as e:
            self._send(500, {"error": str(e)})

    def _send(self, status, data):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()
