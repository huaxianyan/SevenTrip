<p align="center">
  <img src="site/assets/icon.svg" width="112" height="112" alt="出行应用图标">
</p>

<h1 align="center">出行</h1>

<p align="center">把散落在邮件和截图里的乘车信息，整理成清晰、可提醒的本地行程。</p>

<p align="center">
  <a href="https://chuxing.neko7ina.com/">应用主页</a> ·
  <a href="https://github.com/huaxianyan/WalletAssistant/releases">下载</a> ·
  <a href="https://chuxing.neko7ina.com/privacy/">隐私政策</a> ·
  <a href="https://chuxing.neko7ina.com/support/">支持</a>
</p>

## 关于「出行」

「出行」是一款 Android 行程管理应用。它从用户主动授权的数据源导入行程，在设备端完成解析、确认、保存和提醒，让乘车信息不再埋在邮件、截图和不同应用中。

当前版本首先支持中国铁路 12306 邮件，可识别购票、改签、候补兑现和退票通知。用户可以配置支持 IMAP 的邮箱，也可以粘贴邮件正文或选择截图。邮箱同步发现的行程会直接保存，粘贴和截图的识别结果可以先检查再保存，应用不会猜测缺失信息。

```text
IMAP 邮箱 / 粘贴正文 / 截图
            ↓
      设备端解析与保存
            ↓
       本地行程与提醒
            ↓
   可选添加至 Google Wallet
```

## 主要能力

### 从熟悉的来源导入

- <strong>邮箱同步：</strong>用户自行配置支持 TLS IMAP 和专用密码或授权码的邮箱，首次读取来自 `12306@rails.com.cn` 的铁路订单通知，后续只读取新增邮件
- <strong>可选自动同步：</strong>完成首次手动同步后，可按 1～24 小时间隔检查新邮件，发现行程时直接保存并通知结果，下次打开应用就能在行程页看到
- <strong>粘贴正文：</strong>无需配置邮箱，直接解析复制的购票、改签、候补或退票通知
- <strong>截图 OCR：</strong>通过 Android 系统照片选择器选择单张截图，使用设备端中文 OCR 读取文字
- <strong>粘贴与截图先确认：</strong>这两类结果先检查文字和行程字段，再决定是否写入本地行程

### 减少邮箱读取范围

用户可以单独准备一个只接收铁路通知的邮箱，再在「出行」中配置该邮箱，以减少应用获准访问的其他邮件。

应用默认自动选择邮箱的全部邮件文件夹或收件箱。用户也可以从服务器提供的文件夹列表中指定一个同步文件夹，应用只检查所选文件夹。为了持续收到新通知，用户需要自行在邮箱服务商处配置收信规则或过滤器，确保来自 `12306@rails.com.cn` 的邮件会进入所选文件夹。不同服务商的配置方法并不相同，请参考对应邮箱的官方文档。

首次同步或更改同步文件夹后，用户可以选择仅同步未出发行程，或同时导入历史行程并直接归档。完成一次手动全量同步并建立同步位置后，才能开启自动同步。

### 一眼看到出行概况

- 首页显示下一趟行程、发车时间和实时倒计时
- 统计本地已保存行程的出行次数，区分总计、今年和本月
- 按累计次数排列常坐线路，并给出走过多少座车站
- 统计只覆盖本地已保存的行程，可以按页面提示导入历史行程

### 用一个应用管理行程

- 在行程页查看未来行程和乘车关键信息，可左右滑动切换到历史行程
- 同一订单中的不同车次或出发时间会拆分为独立行程
- 同车次、同时间和同路线的多位乘车人会合并显示
- 改签后保留原行程状态，部分退票只影响对应车票
- 查看路线、车次、时间、座位、席别、乘车人和订单信息
- 长按行程可以归档，并可从历史行程中恢复
- 删除不再需要的本地行程，之后仍可重新导入
- 可选在未来行程出发后自动归档，不追溯此前已经结束的行程

### 在需要时提醒

提醒默认关闭，由用户决定是否为新行程或单个行程开启。

- 发车前提醒支持 30 分钟～12 小时，默认 3 小时
- Android 16 支持发车前 15～60 分钟显示实时状态，默认 30 分钟
- 实时状态以 5 分钟为调整粒度，可以设置为 20 分钟
- 「已上车」结束提醒并归档行程
- 「取消提醒」只结束提醒，不影响独立的自动归档任务
- 精确提醒使用 Android 「闹钟和提醒」权限，但不会在系统时钟中创建闹钟

### 可选添加至 Google Wallet

用户确认行程后，可以主动将最少乘车信息添加至 Google Wallet Generic Pass。Wallet 卡片显示路线、车次、时间、座位、席别和乘车人，是应用内行程的可选补充，不承担主要提醒职责。

「出行」不会处理、复制或模拟 12306 动态检票二维码，Wallet 卡片也不是车票或乘车凭证。

## 隐私优先

- 邮箱地址和授权码加密保存在设备上，原始邮件和候选邮件不发送到开发者运营的服务器
- 后台同步只把结构化行程和同步位置写入本地记录，不保存原始邮件正文
- 导入截图和 OCR 文字在 Android 设备端处理
- 应用不申请访问整个照片库，只读取用户本次明确选择的图片
- 行程保存在应用私有的本地数据库中
- 本地行程不纳入 Android 应用数据云备份
- 只有用户明确选择添加至 Google Wallet 时，已确认的最少行程字段才会提交给 Google Wallet

完整说明见 [隐私政策](https://chuxing.neko7ina.com/privacy/)。

## 当前支持范围

- Android 8.0 或更高版本
- 首个数据来源：支持 TLS IMAP 和专用密码或授权码的邮箱
- 首个内容格式：12306 购票、改签、候补兑现和退票邮件
- 支持邮件正文截图的设备端中文 OCR
- 其他 12306 页面截图可能缺少形成完整行程所需的信息
- 不保证第三方 Wallet 卡片显示在系统 At a Glance 区域

## 安装

正式 APK 只通过 [GitHub Releases](https://github.com/huaxianyan/WalletAssistant/releases) 发布，不通过 Google Play 分发。下载后请核对对应 Release Notes 中公布的 SHA-256。

```text
应用名称：出行
包名：com.neko7ina.wallet.assistant
最低系统：Android 8.0
```

## 开发

项目使用 Kotlin、Jetpack Compose、Room、ML Kit 和 Google Play services，包含两个模块：

- `core`：统一行程模型和与 Android 无关的内容解析
- `app`：Android 界面、数据导入、本地存储、提醒和 Wallet 输出

本地构建需要 JDK 17 和 Android SDK 36：

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

Google Cloud、Wallet 和 Release 签名使用本机配置，不提交到仓库。维护文档：

- [Google Cloud 与 Wallet 配置](docs/google-cloud.md)
- [Android Release 签名与恢复](docs/signing.md)
- [Cloudflare Pages 部署](docs/cloudflare-pages.md)
- [`v1.0.0` Release Notes 草稿](docs/release-notes-v1.0.0.md)
- [更新记录](CHANGELOG.md)

## 许可证

仓库中由 NeKo7inA 持有版权且未另行标注的源代码、资源和文档，按 [GNU GPL version 3 或任何后续版本](LICENSE) 授权。

项目依据 GPLv3 第 7 节提供有限的 [Google SDK 链接例外](LICENSE-EXCEPTION.md)。该例外只允许链接和分发项目使用的 Google 专有 SDK，不改变这些组件自身的许可证或服务条款。

第三方组件和素材继续适用各自条款，详见 [第三方许可声明](THIRD_PARTY_NOTICES.md)。

## 独立应用声明

「出行」由 NeKo7inA 独立开发，与中国国家铁路集团有限公司、12306、Google 或其关联机构不存在隶属、合作或官方授权关系。第三方名称仅用于说明数据来源、兼容性和功能边界。
