# StudyMind Backend 部署到 Railway 指南

## 一、准备工作

1. 注册 [Railway](https://railway.app)，用 GitHub 登录
2. 确保 backend 代码在 GitHub 仓库中（可单独建 repo 或使用 monorepo）

## 二、创建项目

1. 在 Railway 控制台点击 **New project**
2. 选择 **Deploy from GitHub repo**
3. 连接 GitHub，选择你的仓库
4. **重要**：如果 backend 在子目录，在 Settings 里设置 **Root Directory** 为 `backend`

## 三、配置环境变量

在 Railway 项目 → **Variables** 中添加：

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `OPENAI_API_KEY` | OpenAI API Key（Whisper 转录用） | ✅ |
| `GEMINI_API_KEY` | Gemini API Key（YouTube 视频分析 fallback） | 可选 |
| `TRANSCRIPT_API_TOKEN` | youtube-transcript.io 的 token（减少 429） | 可选 |

**至少配置 `OPENAI_API_KEY`**，否则 Whisper 无法工作。

## 四、获取部署 URL

1. 部署完成后，在 **Settings** → **Networking** 中点击 **Generate Domain**
2. 会得到类似 `https://xxx-production-xxxx.up.railway.app` 的地址
3. **无需** 在末尾加 `/api`，直接使用根地址

## 五、配置 Android 应用

在 `local.properties` 中设置：

```properties
TRANSCRIPT_BACKEND_URL=https://你的项目.up.railway.app
```

例如：
```properties
TRANSCRIPT_BACKEND_URL=https://studymind-backend-production-abc123.up.railway.app
```

**不要** 配置 `OPENAI_API_KEY`（留空），这样 key 只存在 Railway 服务器上，APK 中不会包含。

## 六、验证部署

在浏览器访问：
- `https://你的域名/health` → 应返回 `{"status":"ok"}`

## 七、费用说明

- 新用户有 $5 免费额度（约 30 天）
- Hobby 计划 $5/月，含 $5 使用额度
- Whisper 按 OpenAI 计费，每次转录约 $0.006/分钟
- Railway 本身按 CPU/内存/流量计费，小项目通常在免费额度内

## 八、常见问题

**Q: 部署失败？**  
A: 确认 `backend` 目录下有 `requirements.txt`、`main.py`、`Procfile`。

**Q: 请求超时？**  
A: Railway 单次 HTTP 请求最长 5 分钟，10 分钟音频分片后每片上传应远低于此。

**Q: 和 Vercel 的区别？**  
A: Railway 无 4.5MB 请求体限制，可处理更大音频；Vercel 免费但限制严格。
