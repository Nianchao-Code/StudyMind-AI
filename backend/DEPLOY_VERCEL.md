# Vercel 部署教程

## 一、部署前准备

### 1. 安装 Node.js
如果还没装，去 [nodejs.org](https://nodejs.org) 下载安装。

### 2. 安装 Vercel CLI
```bash
npm install -g vercel
```

---

## 二、部署方式（二选一）

### 方式 A：命令行部署（最快）

1. 打开终端，进入 backend 目录：
   ```bash
   cd "c:\Users\Nianchao\OneDrive\Desktop\StudyMind AI\backend"
   ```

2. 执行部署：
   ```bash
   vercel
   ```

3. 首次部署会提示：
   - **Set up and deploy?** → 选 `Y`
   - **Which scope?** → 选你的账号
   - **Link to existing project?** → 选 `N`（新建项目）
   - **Project name?** → 直接回车（用默认名）或输入如 `studymind-transcript`
   - **In which directory is your code located?** → 直接回车（当前目录 `.`）

4. 部署完成后会显示：
   ```
   ✅ Production: https://studymind-transcript-xxx.vercel.app
   ```
   **复制这个 URL**（不要带末尾斜杠）

---

### 方式 B：GitHub + Vercel 网页部署

1. 把项目推到 GitHub（如果还没有）：
   ```bash
   cd "c:\Users\Nianchao\OneDrive\Desktop\StudyMind AI"
   git init
   git add .
   git commit -m "Initial commit"
   # 在 GitHub 新建仓库后：
   git remote add origin https://github.com/你的用户名/StudyMind-AI.git
   git push -u origin main
   ```

2. 打开 [vercel.com](https://vercel.com) → 登录（用 GitHub）

3. 点击 **Add New** → **Project**

4. 选择你的仓库 → **Import**

5. **重要**：在配置里设置 **Root Directory**：
   - 点击 `Edit` 展开
   - Root Directory 填：`backend`
   - 这样 Vercel 会从 `backend` 目录部署

6. 点击 **Deploy**，等待完成

7. 部署成功后，复制项目 URL（如 `https://studymind-ai-xxx.vercel.app`）

---

## 三、你需要改什么

### 1. 修改 `local.properties`

在项目根目录的 `local.properties` 里，找到或添加：

```
TRANSCRIPT_BACKEND_URL=https://你的项目名.vercel.app/api
```

**示例**（假设部署后 URL 是 `https://studymind-transcript-abc123.vercel.app`）：
```
TRANSCRIPT_BACKEND_URL=https://studymind-transcript-abc123.vercel.app/api
```

**注意**：
- 末尾必须加 `/api`（因为接口路径是 `/api/transcript`）
- 不要加末尾斜杠

### 2. 重新构建 Android 项目

在 Android Studio：
1. **Build** → **Clean Project**
2. **Build** → **Rebuild Project**
3. 运行到手机或模拟器

---

## 四、验证部署是否成功

### 方法 1：浏览器测试
在浏览器打开：
```
https://你的项目名.vercel.app/api/transcript?video_id=dQw4w9WgXcQ
```

如果返回 JSON（包含 `transcript` 字段），说明部署成功。

### 方法 2：在 App 里测试
1. 打开 StudyMind AI
2. 输入任意有字幕的 YouTube 链接
3. 点击 **Analyze Video**
4. 若进度显示 "Fetching transcript (backend)..." 且能正常生成笔记，则配置正确

---

## 五、常见问题

| 问题 | 解决 |
|------|------|
| `vercel` 命令找不到 | 确认 Node.js 已安装，重新执行 `npm install -g vercel` |
| 部署失败 / 构建错误 | 确认在 `backend` 目录下执行，且 `api/transcript.py` 和 `requirements.txt` 存在 |
| App 仍用旧方式获取字幕 | 检查 `local.properties` 里 `TRANSCRIPT_BACKEND_URL` 是否填写正确，并执行 Clean + Rebuild |
| 接口返回 404 | 确认 URL 末尾有 `/api`，完整路径是 `https://xxx.vercel.app/api` |

---

## 六、目录结构参考

```
StudyMind AI/
├── app/                    # Android 代码
├── backend/                # 部署到 Vercel 的目录
│   ├── api/
│   │   └── transcript.py   # Serverless 函数 → /api/transcript
│   ├── requirements.txt
│   └── ...
├── local.properties        # 在这里配置 TRANSCRIPT_BACKEND_URL
└── ...
```
