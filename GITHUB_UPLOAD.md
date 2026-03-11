# 上传代码到 GitHub 教程

## 一、在 GitHub 创建仓库

1. 点击 **Create repository**（你截图里已经填好了）
2. 保持默认：Public、不勾选 README、不勾选 .gitignore
3. 创建完成后，会看到空仓库页面，**先不要关**

---

## 二、在本地执行命令

打开 **PowerShell** 或 **命令提示符**，依次执行：

### 1. 进入项目目录
```powershell
cd "c:\Users\Nianchao\OneDrive\Desktop\StudyMind AI"
```

### 2. 初始化 Git
```powershell
git init
```

### 3. 添加所有文件（.gitignore 会自动排除 local.properties 等）
```powershell
git add .
```

### 4. 第一次提交
```powershell
git commit -m "Initial commit: StudyMind AI app + transcript backend"
```

### 5. 设置主分支名称（如用 main）
```powershell
git branch -M main
```

### 6. 添加远程仓库

把下面的 `Nianchao-Code` 和 `StudyMind-AI` 换成你实际仓库名（GitHub 会自动把 "StudyMind AI" 转成 StudyMind-AI）：

```powershell
git remote add origin https://github.com/Nianchao-Code/StudyMind-AI.git
```

### 7. 推送代码
```powershell
git push -u origin main
```

---

## 三、可能遇到的问题

1. **提示输入 GitHub 账号密码**  
   - 现在 GitHub 不支持密码，改用 **Personal Access Token**  
   - 在 GitHub → Settings → Developer settings → Personal access tokens 创建  
   - 推送时用 token 代替密码

2. **提示 "fatal: not a git repository"**  
   - 先执行 `git init`

3. **推送后看不到 local.properties**  
   - 正常，`.gitignore` 会排除它，避免泄露 API 密钥  
   - 别人克隆后把 `local.properties.example` 复制为 `local.properties` 再填自己的密钥

4. **仓库名不是 StudyMind-AI**  
   - 在 GitHub 新建仓库页面查看实际 URL，把第 6 步的 `StudyMind-AI` 换成你的仓库名
