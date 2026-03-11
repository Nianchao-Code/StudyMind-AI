# Transcript & Audio Backend

- `/api/transcript?video_id=xxx` — YouTube transcript (youtube-transcript-api)
- `/api/audio?video_id=xxx` — Audio URL for Whisper fallback (yt-dlp)

Deploy to Vercel: `vercel`

Set `TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api` in `local.properties`.
