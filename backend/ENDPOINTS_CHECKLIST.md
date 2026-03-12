# StudyMind Backend 端点检查清单

## App → Backend 调用对照

| 功能 | App 调用 | Backend 端点 | 请求格式 | 响应格式 |
|------|----------|--------------|----------|----------|
| 生成笔记 (PDF/音频/YouTube transcript) | BackendAIApiClient | POST /summarize | `{prompt, systemPrompt}` | `{response}` |
| 音频转录 (Whisper) | WhisperApiClient | POST /whisper | `{audio: base64, filename, mimeType}` | `{text}` |
| YouTube 字幕 | TranscriptBackendClient | GET /transcript?video_id=&url= | - | `{video_id, transcript}` |
| YouTube 音频 URL | YouTubeAudioExtractor | GET /audio?video_id= | - | `{video_id, url}` |
| YouTube 视频分析 (Gemini fallback) | GeminiYouTubeAnalyzer | POST /gemini_youtube | `{url, title}` | `{notes}` |
| 健康检查 | - | GET /health | - | `{status: "ok"}` |

## Railway 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| OPENAI_API_KEY | ✅ | Whisper + 笔记生成 (summarize) |
| GEMINI_API_KEY | 可选 | YouTube 视频直接分析 (transcript 失败时) |
| TRANSCRIPT_API_TOKEN | 可选 | youtube-transcript.io，减少 429 |

## local.properties (Android)

```properties
TRANSCRIPT_BACKEND_URL=https://xxx.up.railway.app
```

- 不要加 `/api` 后缀
- 配置后，OPENAI_API_KEY 可留空（key 在服务器）
