# Android Release 签名与自动发布

正式包名为 `com.neko7ina.wallet.assistant`。

正式签名证书 SHA-256：

```text
eafaba2f329a58d091d1641630339f2cc3e81bb72c4e3e048fe5f068fe2be2be
```

Release KeyStore 和密码不得进入 Git、Issue、Actions 日志或 Release 附件。完整恢复包保存在：

```text
\\192.168.7.216\homes\NeKo7inA\dev\Android Signing\WalletAssistant
```

## GitHub Actions 自动发布

仓库中的 `.github/workflows/release.yml` 在推送符合 `vX.Y.Z` 格式的 Tag 后执行：

1. 检查 Tag 版本与 `app/build.gradle.kts` 中的 `versionName` 一致
2. 从 GitHub `release` Environment Secret 恢复临时签名目录
3. 使用 JDK 17 执行 `:core:test` 和 `:app:assembleRelease`
4. 使用 `apksigner` 检查 APK 签名证书 SHA-256，并生成 APK 与对应的 `.sha256` 文件
5. 用 `scripts/verify_release_notes.py` 校验 `docs/release-notes-vX.Y.Z.md`，不合规直接失败
6. 把说明文件与追加的 `## 构建信息` 表格拼成 Release 正文，说明文件缺失时同样失败
7. 创建公开的 Latest GitHub Release，标题只留 Tag，并上传两个 Asset

Workflow 不为普通 Push 或 Pull Request 提供签名 Secret。推送 Tag 时创建 Release，从 Actions 页面手动运行时只执行签名构建，并保留 1 天的 Dry-run Artifact，不创建 Release。发布任务使用 GitHub `release` Environment，后续可以在仓库设置中为该 Environment 增加人工审批。

### Environment Secret

`release` Environment 必须存在以下 Secret：

```text
ANDROID_RELEASE_BUNDLE_BASE64
```

它是一个经过 Base64 编码的 `tar.gz`，只包含：

```text
wallet-assistant-release.p12
signing.properties
wallet.properties
```

文件名以 `signing.properties` 中的 `storeFile` 为准。上传 Secret 时不得把 Base64 内容写入仓库文件或终端历史，应通过标准输入直接交给 `gh secret set --env release`。更新 KeyStore、密码或 Wallet 配置后，必须重新生成并替换这个 Secret。

### 发布新版本

1. 提高 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
2. 更新 `CHANGELOG.md`
3. 新建与 Tag 同名的 Release Notes，例如：

   ```text
   docs/release-notes-v1.1.0.md
   ```

   正文不超过 40 行，新功能与问题修复分开成节（至少一节），末尾带一条指向
   `/blob/v<标签>/` 的详细记录链接。提交前先本地跑一次门禁：

   ```bash
   python scripts/verify_release_notes.py docs/release-notes-v1.1.0.md
   ```

4. 完成代码审查和真机验收后提交并推送 `main`
5. 创建并推送带说明的 Tag：

   ```bash
   git tag -a v1.1.0 -m "出行 v1.1.0"
   git push origin v1.1.0
   ```

6. 在 GitHub Actions 中确认 Release Workflow 成功
7. 从 Release 页面下载 APK，并核对页面正文、`.sha256` Asset 和本地计算值一致

如果 Action 在创建 Release 前失败，可以修复配置后重新运行。Release 公开后不得用同一版本号替换 APK，应提高版本号重新发布。

## 本机恢复与手动构建

1. 将 NAS 中 `WalletAssistant` 文件夹完整复制到：

   ```text
   %USERPROFILE%\.android\signing\WalletAssistant
   ```

2. 确认目录包含：

   ```text
   wallet-assistant-release.p12
   signing.properties
   certificate.txt
   wallet.properties
   RECOVERY.txt
   ```

3. 克隆仓库并执行：

   ```bash
   ./gradlew :core:test :app:assembleRelease
   ```

4. 使用 Android SDK 的 `apksigner verify --print-certs` 核对 Release APK 的证书摘要。

本机手动构建只用于恢复和排障。正式公开 Release 默认由 GitHub Actions 生成。
