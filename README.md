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
- **Backend**: Python serverless (Vercel) for YouTube transcript & audio

## Prerequisites

- Android Studio (Arctic Fox+)
- OpenAI API key (required)
- Optional: YouTube Data API v3, Transcript API token, Vercel backend URL, Gemini API key

## Setup

### 1. Clone & configure

```bash
git clone https://github.com/Nianchao-Code/StudyMind-AI.git
cd StudyMind-AI
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
OPENAI_API_KEY=sk-proj-...              # required
YOUTUBE_API_KEY=AIzaSy...               # optional, video title
TRANSCRIPT_API_TOKEN=...                # optional, youtube-transcript.io (first)
TRANSCRIPT_BACKEND_URL=https://...       # optional, Vercel backend
GEMINI_API_KEY=...                      # optional, direct video analysis fallback
```

### 2. Deploy backend (optional)

For YouTube transcript/audio:

```bash
cd backend
vercel
```

Set `TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api` in `local.properties`.

### 3. Build & run

Open in Android Studio, sync Gradle, run on emulator or device.

## YouTube Flow

1. **Transcript API** (youtube-transcript.io) — if `TRANSCRIPT_API_TOKEN` is set
2. **Vercel backend** — if `TRANSCRIPT_BACKEND_URL` is set
3. **Innertube** — direct YouTube API
4. **Watch page** — HTML parsing
5. **Gemini** — direct video analysis (if `GEMINI_API_KEY` is set)
6. **Manual** — download video and import, or paste transcript

## Project Structure

```
├── app/src/main/java/com/studymind/app/
│   ├── agent/       # Content analysis, summarization pipeline
│   ├── api/         # OpenAI client
│   ├── data/        # Room, models
│   ├── pdf/         # PDF extraction
│   ├── whisper/     # Whisper transcription
│   └── youtube/     # Transcript API, backend, Innertube, Gemini
├── backend/         # Vercel serverless (transcript, audio)
└── local.properties.example
```

## License

MIT
