"""
Vercel serverless: POST /api/summarize
Proxies to OpenAI. Body: {prompt, systemPrompt}. Returns {response}.
"""
from http.server import BaseHTTPRequestHandler
import json
import os


def do_openai_chat(prompt: str, system_prompt: str | None) -> dict:
    import urllib.request

    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        return {"error": "OPENAI_API_KEY not configured", "status": 500}

    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    body = json.dumps({
        "model": "gpt-4o-mini",
        "messages": messages,
        "max_tokens": 4096,
    }).encode()

    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions",
        data=body,
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            data = json.loads(resp.read().decode())
            content = ""
            if data.get("choices"):
                msg = data["choices"][0].get("message", {})
                content = msg.get("content", "")
            return {"response": content}
    except Exception as e:
        return {"error": str(e), "status": 500}


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length).decode() if length else "{}"
            body = json.loads(raw)
            prompt = body.get("prompt", "")
            system_prompt = body.get("systemPrompt")
            if not prompt:
                self._send(400, {"error": "prompt required"})
                return
            result = do_openai_chat(prompt, system_prompt)
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
