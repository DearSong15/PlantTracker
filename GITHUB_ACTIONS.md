# GitHub Actions 云端编译 APK 指南

本项目已配置 GitHub Actions 工作流，支持云端自动编译 APK。

## 工作流功能

### 1. 自动触发

- **Push 到 main 分支**: 自动构建 Debug APK
- **Pull Request 到 main 分支**: 自动构建 Debug APK
- **创建标签 (v*)**: 自动构建并签名 Release APK，同时创建 GitHub Release

### 2. 手动触发

进入 GitHub 仓库的 Actions 页面，选择 "Build APK" 工作流，点击 "Run workflow":

- **Build Type**: 选择 `debug` 或 `release`
- **Upload APK**: 是否上传构建产物

## 配置签名 (Release 版本)

要构建签名的 Release APK，需要在仓库设置中添加以下 Secrets：

### 1. 生成签名密钥

```bash
keytool -genkey -v -keystore planttracker.keystore -alias planttracker -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 将密钥转换为 Base64

```bash
base64 -i planttracker.keystore -o keystore.base64
```

### 3. 在 GitHub 仓库中添加 Secrets

进入仓库 Settings -> Secrets and variables -> Actions，添加以下 Secrets：

| Secret 名称 | 说明 |
|------------|------|
| `KEYSTORE_BASE64` | 密钥库的 Base64 编码内容 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

## 使用步骤

### 构建 Debug APK

1. Push 代码到 main 分支，或
2. 手动触发工作流，选择 `debug` 类型

### 构建 Release APK

1. 手动触发工作流，选择 `release` 类型
2. 构建完成后，在 Actions 页面下载 APK

### 发布正式版本

1. 创建并推送标签：
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
2. GitHub Actions 会自动构建签名 APK 并创建 Release

## 下载 APK

构建完成后，可以在以下位置下载 APK：

1. **Actions 页面**: 进入具体的工作流运行记录，在 Artifacts 部分下载
2. **Release 页面**: 如果是标签触发的构建，在 Releases 页面下载

## 工作流文件

工作流配置文件位于：`.github/workflows/build.yml`

## 注意事项

1. 首次运行可能需要较长时间（下载依赖）
2. 后续运行会使用缓存加速
3. Debug APK 保留 30 天，Release APK 保留 90 天
4. 签名密钥请妥善保管，不要上传到代码仓库
