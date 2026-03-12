# StudyMind AI

Android app that turns study materials—PDFs, audio, video, and YouTube—into structured, exam-ready notes using AI.

## Features

- **Multi-source input**: PDF, pasted text, voice recording, or YouTube URL
- **Structured notes**: 5 sections—key definitions, core concepts, formulas, pitfalls, quick review
- **Voice recording**: Record lectures, tap to stop → Whisper transcription → AI notes
- **YouTube**: Transcript API → backend → Innertube → Gemini fallback
- **History**: Save notes, flashcards, and quizzes

## Tech Stack

- **Android**: Java, Material Design 3, Room, OkHttp
- **AI**: OpenAI GPT-4o-mini (notes), Whisper (speech-to-text)
- **Backend**: Python FastAPI on Railway (transcript, whisper, summarize, gemini)

## Quick Start

### 1. Deploy backend (Railway)

```bash
cd backend
railway login
railway init
railway variables set OPENAI_API_KEY=sk-xxx
railway up
railway domain
```

See [backend/RAILWAY_DEPLOY.md](backend/RAILWAY_DEPLOY.md) for details.

### 2. Configure Android app

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
TRANSCRIPT_BACKEND_URL=https://your-project.up.railway.app
```

Leave `OPENAI_API_KEY` empty—the key stays on the server.

### 3. Build & run

Open in Android Studio, sync Gradle, run on device or emulator.

## Backend env vars (Railway)

| Variable | Required | Purpose |
|----------|----------|---------|
| OPENAI_API_KEY | ✅ | Whisper + note generation |
| GEMINI_API_KEY | optional | YouTube video analysis fallback |
| TRANSCRIPT_API_TOKEN | optional | youtube-transcript.io (reduces 429) |

## Project structure

```
├── app/src/main/java/com/studymind/app/
│   ├── agent/       # Content analysis, summarization
│   ├── api/         # OpenAI, backend clients
│   ├── data/        # Room, models
│   ├── pdf/         # PDF extraction
│   ├── whisper/     # Whisper transcription
│   └── youtube/     # Transcript, Gemini
├── backend/         # FastAPI (Railway)
│   ├── main.py      # /transcript, /whisper, /summarize, /gemini_youtube, /audio
│   └── RAILWAY_DEPLOY.md
└── local.properties.example
```

## License

MIT
