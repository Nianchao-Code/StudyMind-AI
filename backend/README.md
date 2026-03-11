# Transcript & Audio Backend

- `/api/transcript?video_id=xxx` — YouTube transcript
  - If `TRANSCRIPT_API_TOKEN` (Vercel env) is set: tries youtube-transcript.io first
  - Fallback: youtube-transcript-api
- `/api/audio?video_id=xxx` — Audio URL (pytubefix)

Deploy to Vercel: `vercel`

In Vercel: add env var `TRANSCRIPT_API_TOKEN` (from youtube-transcript.io) for better reliability.

In `local.properties`: `TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api`
