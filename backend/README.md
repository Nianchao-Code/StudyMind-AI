# StudyMind Transcript Backend

Fetches YouTube transcripts using `youtube-transcript-api` (reliable server-side).

## Deploy on Vercel (recommended)

1. Install Vercel CLI: `npm i -g vercel`
2. Deploy:
   ```bash
   cd backend
   vercel
   ```
3. Follow prompts (link to existing project or create new)
4. Copy the deployed URL (e.g. `https://xxx.vercel.app`)

**Android config** in `local.properties`:
```
TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api
```

## Local development (FastAPI)

```bash
cd backend
pip install -r requirements.txt
pip install fastapi uvicorn
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

- **Android Emulator**: `TRANSCRIPT_BACKEND_URL=http://10.0.2.2:8000`
- **Physical device (same WiFi)**: `TRANSCRIPT_BACKEND_URL=http://YOUR_PC_IP:8000`

## Local development (Vercel)

```bash
cd backend
vercel dev
```

Then use `TRANSCRIPT_BACKEND_URL=http://localhost:3000/api` (or the port shown).
