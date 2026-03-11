"""
Vercel serverless: POST /api/gemini_youtube
Proxies to Gemini for YouTube video analysis. Body: {url, title}. Returns {notes}.
"""
from http.server import BaseHTTPRequestHandler
import json
import os


def do_gemini_youtube(url: str, title: str | None) -> dict:
    import urllib.request

    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        return {"error": "GEMINI_API_KEY not configured", "status": 500}

    prompt = (
        "You are StudyMind AI. Analyze this YouTube video and create EXAM-FOCUSED structured notes. "
        "Output ONLY valid JSON (use \\n for newlines), no markdown:\n"
        '{"keyDefinitions":"...","coreConcepts":"...","importantFormulas":"...","commonPitfalls":"...","quickReview":"..."}\n'
        "keyDefinitions: Exam-relevant terms. • Term\\n  - definition\\n  - exam tip.\n"
        "coreConcepts: Core ideas with 考点. • Concept\\n  - mechanism\\n  - exam point.\n"
        "importantFormulas: Formulas/code. • Topic\\n  - when to use\\n  - key syntax.\n"
        "commonPitfalls: Exam mistakes. • Category\\n  - wrong answer\\n  - correct approach.\n"
        "quickReview: 考点总结—5–7 concepts. • Topic\\n  - key point.\n"
        "MUST populate ALL 5 sections. Never use N/A. Use • for main topics and - for sub-points."
    )

    body = {
        "contents": [{
            "parts": [
                {"fileData": {"fileUri": url}},
                {"text": f"Video: {title or 'YouTube'}\n\n{prompt}"},
            ]
        }],
        "generationConfig": {"temperature": 0.4, "maxOutputTokens": 8192},
    }

    req = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={key}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode())
            cands = data.get("candidates", [])
            if not cands:
                return {"error": "No candidates", "status": 500}
            parts = cands[0].get("content", {}).get("parts", [])
            if not parts:
                return {"error": "No parts", "status": 500}
            text = parts[0].get("text", "")
            return {"notes": text}
    except Exception as e:
        return {"error": str(e), "status": 500}


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length).decode() if length else "{}"
            body = json.loads(raw)
            url = body.get("url", "").strip()
            title = body.get("title")
            if not url:
                self._send(400, {"error": "url required"})
                return
            result = do_gemini_youtube(url, title)
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
