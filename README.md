# StudyMind AI

An Android application that transforms study materials—PDFs, audio, video, and YouTube content—into structured, exam-ready notes using AI-powered summarization.

## Features

- **Multi-source input**: Import PDFs, paste text, record voice, or add YouTube URLs
- **Structured notes**: Generates 5 modules—key definitions, core concepts, formulas, pitfalls, and quick review
- **YouTube transcripts**: Fetches captions via optional backend (Vercel) or built-in fallbacks
- **Whisper integration**: Transcribes audio/video with OpenAI Whisper when transcripts are unavailable
- **History & Q&A**: Save notes, generate flashcards, and quiz yourself

## Tech Stack

- **Android**: Kotlin/Java, Material Design 3, Room, Retrofit
- **AI**: OpenAI GPT-4o-mini (summarization), Whisper (speech-to-text)
- **Backend**: Python serverless (Vercel) for YouTube transcript fetching

## Prerequisites

- Android Studio (Arctic Fox or later)
- OpenAI API key
- Optional: YouTube Data API v3 key (for video metadata)
- Optional: Transcript backend URL (for reliable YouTube captions)

## Setup

### 1. Clone and configure

```bash
git clone https://github.com/Nianchao-Code/StudyMind-AI.git
cd StudyMind-AI
```

Copy the example config and add your keys:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
OPENAI_API_KEY=sk-proj-...
YOUTUBE_API_KEY=AIzaSy...          # optional
TRANSCRIPT_BACKEND_URL=https://... # optional, for YouTube transcript backend
```

### 2. Deploy transcript backend (optional)

For reliable YouTube transcript fetching, deploy the backend to Vercel:

```bash
cd backend
npm i -g vercel
vercel
```

Set `TRANSCRIPT_BACKEND_URL=https://your-project.vercel.app/api` in `local.properties`.

### 3. Build and run

Open the project in Android Studio, sync Gradle, and run on an emulator or device.

## Project Structure

```
├── app/                    # Android application
│   └── src/main/java/com/studymind/app/
│       ├── agent/          # AI pipeline (content analysis, summarization)
│       ├── api/             # OpenAI client
│       ├── data/            # Room database, models
│       ├── pdf/             # PDF text extraction
│       ├── whisper/         # Whisper transcription
│       └── youtube/         # YouTube transcript fetching
├── backend/                 # Transcript API (Vercel serverless)
│   └── api/transcript.py
└── local.properties.example
```

## License

MIT
