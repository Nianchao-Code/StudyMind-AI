# StudyMind AI

An Android app that turns study materials—PDFs, audio, video, and YouTube—into structured, exam-ready notes using AI.

## Features

- **Multi-source input**: PDF, pasted text, voice recording, or YouTube URL
- **Structured notes**: 5 sections—key definitions, core concepts, formulas, pitfalls, quick review
- **YouTube analysis**: Transcript API → Vercel backend → Innertube → Watch page → Gemini (direct video analysis)
- **Fallback**: When all sources fail, prompts you to download the video and import it, or paste transcript manually
- **History**: Save notes, flashcards, and quizzes

## Tech Stack

- **Android**: Java, Material Design 3, Room, Retrofit, OkHttp
- **AI**: OpenAI GPT-4o-mini (summarization), Whisper (speech-to-text for imported audio/video)
- **Backend**: Python serverless (Vercel) for transcript, audio, summarize, Gemini, Whisper

## Prerequisites

- Android Studio (Arctic Fox+)
- Vercel backend with env vars: `OPENAI_API_KEY`, `GEMINI_API_KEY`, `TRANSCRIPT_API_TOKEN` (optional)

## Setup

### 1. Deploy backend (required for distribution)

```bash
cd backend
vercel
```

In Vercel: add env vars `OPENAI_API_KEY`, `GEMINI_API_KEY`, `TRANSCRIPT_API_TOKEN` (optional).

### 2. Clone & configure

```bash
git clone https://github.com/Nianchao-Code/StudyMind-AI.git
cd StudyMind-AI
cp local.properties.example local.properties
```

Edit `local.properties`:

**Distribution (APK for others):** Only need backend URL. No API keys in APK.
```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api
```

**Dev (local testing without backend):**
```properties
OPENAI_API_KEY=sk-proj-...
TRANSCRIPT_BACKEND_URL=...   # or omit to use direct OpenAI
GEMINI_API_KEY=...          # optional, for YouTube fallback
```

### 3. Build & run

Open in Android Studio, sync Gradle, run on emulator or device.

## YouTube Flow

1. **Vercel backend** (transcript.io → youtube-transcript-api)
2. **Innertube** → **Watch page** (fallbacks, skipped on 429)
3. **Backend Gemini** (direct video analysis) — **requires `GEMINI_API_KEY` in Vercel**
4. **Manual** — download video and import, or paste transcript

## Project Structure

```
├── app/src/main/java/com/studymind/app/
│   ├── agent/       # Content analysis, summarization pipeline
│   ├── api/         # OpenAI client
│   ├── data/        # Room, models
│   ├── pdf/         # PDF extraction
│   ├── whisper/     # Whisper transcription
│   └── youtube/     # Transcript API, backend, Innertube, Gemini
├── backend/         # Vercel serverless (transcript, audio, summarize, gemini, whisper)
└── local.properties.example
```

## License

MIT
