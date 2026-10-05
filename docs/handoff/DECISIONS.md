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

`docs/release-notes-v<版本>.md` 控制在 40 行以内，只写概述、主要更新，
以及一条指向 `/blob/v<标签>/` 下真实存在文档的链接。Release 标题就是标签本身，
不带产品名前缀。正文由 CI 在说明文件之后追加 `## 构建信息` 表格。

依据是用户技能 `release-notes-standard`。使用方式、验收过程、构建范围这类长期内容
放 `README.md` 或 `docs/`，不写进 Release 正文。

门禁脚本是 `scripts/verify_release_notes.py`，CI 在发布前跑。

发布说明目录沿用 `docs/release-notes-v<版本>.md` 的扁平命名，
不使用标准默认的 `docs/release-notes/` 子目录。CI 里显式传文件路径。

## 版本号与产物命名

`versionCode` 严格递增，否则无法覆盖安装。产物命名 `chuxing-<版本>.apk`，
同时产出 `chuxing-<版本>.apk.sha256`。

`versionName` 与 `versionCode` 不要求一一对应。1.2.0 的 `versionCode` 取 5，
因为用于真机验收的构建就是 5，沿用它可以省掉一次重装。

## 进度文档的形态

进度记录放 `docs/handoff/`：长决策在本文件，每日流水在
`docs/handoff/YYYY-MM-DD.md`，两者都入库，任何工具克隆仓库都能读到。

内网地址、设备序列号、凭据位置写本机 `AGENTS.md`（已被 `.gitignore` 忽略），
不写进这里。
