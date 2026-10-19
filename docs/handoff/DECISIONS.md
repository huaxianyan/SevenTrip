# 决策记录

只记长期有效的决定。被推翻时就地修订，不保留新旧两份说法。

## 解析规则改动必须递增 `parser.version`

改动任一 `TravelDocumentParser` 的匹配规则时，同时递增该解析器的 `version`。

`parser.version` 是 IMAP 同步断点 key 的一部分（见 `AppPreferences.imapSyncCheckpoint`）。
不递增的话，已经扫过的历史邮件不会重新解析，修复对存量用户不生效。

代价是递增会让下次同步重扫全部历史邮件。重扫不产生重复行程，
依据是 `TravelDocumentRepository.replaceReservations` 按
`(provider.code, reservation.reference)` 整组替换、按行程主键覆盖，
且 `archived` 与 `reminderEnabled` 继承旧值。

## 发布说明以短正文加详细记录链接为准

`docs/release-notes-v<版本>.md` 控制在 40 行以内，只写概述、新功能与问题修复分节，
以及一条指向 `/blob/v<标签>/` 下真实存在文档的链接。Release 标题就是标签本身，
不带产品名前缀。正文由 CI 在说明文件之后追加 `## 构建信息` 表格。

新功能与问题修复分开描述，读者才能一眼看出哪些是新增能力、哪些是原有缺陷被修。
至少出现其中一节，纯功能或纯修复的版本不必为了凑齐两节硬归类。
门禁脚本是 `scripts/verify_release_notes.py`，回归测试是 `scripts/test_verify_release_notes.py`。
`--summary-heading` 参数已随规范更新移除，不要用旧的合并节名绕过分类。

依据是用户技能 `release-notes-standard`。使用方式、验收过程、构建范围这类长期内容
放 `README.md` 或 `docs/`，不写进 Release 正文。

发布说明目录沿用 `docs/release-notes-v<版本>.md` 的扁平命名，
不使用标准默认的 `docs/release-notes/` 子目录。CI 里显式传文件路径。

## 版本号与产物命名

`versionCode` 严格递增，否则无法覆盖安装。产物命名 `SevenTrip-<版本>.apk`，
同时产出 `SevenTrip-<版本>.apk.sha256`。

`versionName` 与 `versionCode` 不要求一一对应。当前 `versionCode` 为 6（`1.3.0`），
从真机验收构建所用的 5 递增而来。

## 进度文档的形态

进度记录放 `docs/handoff/`：长决策在本文件，每日流水在
`docs/handoff/YYYY-MM-DD.md`，两者都入库，任何工具克隆仓库都能读到。

内网地址、设备序列号、凭据位置写本机 `AGENTS.md`（已被仓库本地 `.git/info/exclude` 忽略），
不写进这里。

## 状态栏胶囊的走秒不由本应用修补

实时提醒的倒计时基准是固定的出发时刻（`TripReminderReceiver` 里的 `setWhen(displayEnd)`），
不是递增计数。通知被重新发布时胶囊画面会重启动，数值不会算错。

胶囊的走秒平滑度由平台决定。已知缺陷是 SystemUI 的状态栏计时器走秒卡顿，
缺陷号 `450909625`，AOSP 以标志 `com.android.systemui.status_bar_chronometer_fixes` 修复。
设备当前版本实测走秒均匀：19 次跳变间隔都是 1.000 秒，抖动 ±7 毫秒。

以后真机上再看到跳变，先按平台问题处理，不在本应用里做补偿。
若用户不愿再看到逐秒跳动，备选方案是把胶囊文案换成固定的出发时间，代价是失去实时倒计时。

测法与依据见 `docs/handoff/2026-10-16.md`。
