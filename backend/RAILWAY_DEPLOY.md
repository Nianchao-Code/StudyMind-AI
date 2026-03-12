# StudyMind Backend - Railway CLI 部署指南

## 一、安装 Railway CLI

**Windows (无 Node.js):** 下载 [Releases](https://github.com/railwayapp/cli/releases) 中 `railway-v*-x86_64-pc-windows-msvc.zip`，解压到 `C:\railway`，然后执行：
```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\railway", "User")
```

**npm (需 Node.js):**
```bash
npm install -g @railway/cli
```

## 二、登录并部署

```bash
cd backend
railway login
railway init
railway up
```

- `railway login`：浏览器打开登录 Railway
- `railway init`：创建新项目或关联已有项目（选 "Create new project"）
- `railway up`：部署当前目录（backend）到 Railway

## 三、配置环境变量

```bash
railway variables set OPENAI_API_KEY=sk-你的key
railway variables set GEMINI_API_KEY=你的gemini_key   # 可选
```

或登录 Railway 网页 → 项目 → Variables 里添加。

## 四、生成公网域名

```bash
railway domain
```

会生成类似 `https://xxx.up.railway.app` 的地址。

## 五、配置 Android 应用

在项目根目录的 `local.properties` 中：

```properties
TRANSCRIPT_BACKEND_URL=https://你的域名.up.railway.app
```

**不要** 配置 `OPENAI_API_KEY`，留空即可。

## 六、验证

访问 `https://你的域名/health`，应返回 `{"status":"ok"}`。

## 七、后续更新部署

```bash
cd backend
railway up
```
