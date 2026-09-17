# Dashboard 页面调研

调研日期：2026-09-14
调研对象：「出行」`com.neko7ina.wallet.assistant`，当前版本 `1.0.1`（`v1.0.1` Tag，工作区干净）

---

## 一、结论先说

七叔要的方向是对的：现在应用只有「行程列表」一个入口，出行次数、即将出发这类信息完全看不到。加一个 Dashboard 首页有实际价值，不是装饰。

但有三个必须先说清楚的事实，它们直接决定这个功能能做到什么程度：

1. **统计只能覆盖「本地已导入的行程」**。首次邮箱同步时如果用户选了「仅同步未出发」（`AGENTS.md` 里写明的默认选项之一），本地库里就只有未出发行程，Dashboard 会显示 0 次出行。所以 Dashboard 必须配套一个「导入历史行程」的引导，否则第一印象就是个空壳。这是本次调研里最容易被忽略、影响最大的一条。

2. **出行里程做不到，票款总额有口径陷阱**。12306 通知里没有里程字段，现有解析器也取不到（详见 3.2、3.3）。别为了页面好看硬编一个数字。

3. **底栏导航不要只放两项**。Material Design 3 明确要求 NavigationBar 承载 3～5 个目的地，少于 3 个应改用页内 tabs。恰好现在「设置」和「历史行程」都埋在首页右上角的三点菜单里（`TravelWalletApp.kt:634-654`），借这次改造把它们提到底栏，既满足规范又顺手解决了入口过深的问题。

技术实现上我建议**不动 Room 表结构**，在 Kotlin 侧对已解码的行程聚合，聚合逻辑放进 `core` 模块写成纯函数，这样能直接进 `:core:test`。理由在第五节。

---

## 二、现状盘点

### 2.1 导航结构

全项目**没有引入任何导航库**，没有 `navigation-compose`，没有 `NavHost`，也没有 `NavigationBar`（全仓库 grep 无命中）。导航是手写的：

| 位置 | 内容 |
|---|---|
| `TravelWalletApp.kt:126-135` | `private enum class Screen`，8 个页面常量 |
| `TravelWalletApp.kt:142-149` | `Screen.depth`，用整数决定转场方向 |
| `TravelWalletApp.kt:199` | `var screen by rememberSaveable { mutableStateOf(Screen.HOME) }` 单一状态源 |
| `TravelWalletApp.kt:272-278` | `BackHandler`，所有二级页面返回 `Screen.HOME` |
| `TravelWalletApp.kt:280-292` | `AnimatedContent` 按 `depth` 比较做水平滑入滑出 |

首页 `HomeScreen`（`TravelWalletApp.kt:613-716`）的结构是：`TopAppBar` 标题「我的行程」+ 右上角 `MoreVert` 下拉菜单（里面是「历史行程」和「设置」）+ `FloatingActionButton`「添加行程」+ 行程卡片列表。

也就是说，**当前 App 是「首页即行程列表」的单入口结构**，这次改造是把顶层结构从「1 个入口」变成「2～3 个 tab」。

### 2.2 数据层

Room 只有一张主表，8 列（`StoredTravelDocument.kt:14-23`，schema 版本 6 见 `app/schemas/com.neko7ina.wallet.assistant.data.TravelWalletDatabase/6.json:9`）：

```text
id, providerCode, reservationReference, departureEpochMillis,
payload, updatedAtEpochMillis, reminderEnabled, archived
```

**关键点：除了 `departureEpochMillis`，所有行程内容都在 `payload` 这一列里，是一个 JSON 字符串。**

`payload` 里装的是 `core` 模块的 `TravelDocument`（`TravelDocument.kt:8-17`），字段全貌：

- `provider.code` / `provider.name`
- `reservation.reference` / `reservation.purchasedOn`（购票日期）/ `reservation.totalPrice`（订单级票款）
- `travelers[].id` / `travelers[].name`
- `segments[].origin.name` / `destination.name` / `departureTime` / `arrivalTime` / `serviceNumber` / `seatAssignments[]`（`section` 车厢、`seat` 座位、`category` 席别、`status`）/ `attributes`
- `status`（`CONFIRMED` / `RESCHEDULED` / `REFUNDED`）

DAO 只有两个列表查询（`TravelDocumentDao.kt:12-16`）：`observeActive()` 按出发时间升序、`observeArchived()` 按出发时间降序。**没有任何聚合查询。**

好消息是 ViewModel 已经把两张表全量解码好了（`TravelWalletViewModel.kt:94-108`），Dashboard 直接 `combine(documents, archivedDocuments)` 就有完整数据，不需要新增 DAO 方法。

### 2.3 已经被验证过的能力

- `hasDeparted()`（`TravelDocumentStatus.kt:6-7`）：用 `segments.minOf { departureTime }` 和当前时刻比较，可以直接复用来判断「已出行」。
- `stableId()`：行程唯一标识，列表 key 已经在用。
- `DEPARTURE_FORMAT` / `COMPACT_DATE_FORMAT` / `COMPACT_TIME_FORMAT`：现有的时间格式常量（`TravelWalletApp.kt:2019-2020` 附近）。
- 时区约定：中国铁路时间统一用 `Asia/Shanghai`（`ChinaRailwayEmailParser.kt:266`）。**但 UI 层的时间格式化用的是 `ZoneId.systemDefault()`**（`TravelWalletApp.kt:151-153`）。统计口径要注意这个不一致。

---

## 三、统计指标的数据可行性

### 3.1 可行性矩阵

| 想做的指标 | 数据来源 | 可行性 | 说明 |
|---|---|---|---|
| 出行总次数 / 今年 / 本月 | `departureEpochMillis` 列 | 可做，直接用 | 注意口径，见 3.4 |
| 即将出发（下一趟） | `observeActive()` 首条 | 可做，直接用 | 已按出发时间升序，取第一条即可 |
| 发车倒计时 | `departureTime - now` | 可做，但要新增 ticker | 现有代码里没有任何随时间自动刷新的机制，倒计时需要自己驱动重组 |
| 年度 / 月度出行趋势折线 | `departureEpochMillis` 列 | 可做 | 需要补零的空月处理，否则折线会断裂 |
| 常坐线路 Top N | `payload.segments[0].origin/destination.name` | 可做，需解码 | 已在内存里，成本为零 |
| 常坐车次 | `payload.segments[0].serviceNumber` | 可做，需解码 | |
| 席别分布 | `payload.segments[0].seatAssignments[].category` | 可做，需解码 | |
| 乘车人排行 | `payload.travelers[].name` | 可做，需解码 | 姓名不出设备，符合隐私约定 |
| 已改签 / 已退票统计 | `payload.status` | 可做 | |
| 走过多少车站 | `origin.name` / `destination.name` 去重 | 可做 | 这是里程的合理替代品 |
| **出行里程** | 无 | **做不到** | 见 3.2 |
| **行程时长** | `arrivalTime` 恒为 null | **做不到** | 见 3.2 |
| 票款合计 | `reservation.totalPrice` | **有陷阱** | 见 3.3 |

### 3.2 里程和时长：没有数据源

**里程**：12306 通知正文里没有里程。现有解析器的正则一共 14 个捕获组（`ChinaRailwayEmailParser.kt:287`），依次是乘车人、年、月、日、时、分、出发站、到达站、车次、车厢、座位、席别、票种、票价，**没有任何一项是里程**。要显示里程只有两条路：

- 自建「站到站」里程表。全国 3000 多个车站的两两距离，数据要自己维护、自己校对，而且来源没有权威公开接口。
- 按线路粗估。这就属于猜测，直接违反 `AGENTS.md` 里「缺失信息不猜测」的实现原则。

**建议不做**。用「走过 N 座车站」「最常坐的线路」替代，信息量不差，而且全部有据可查。

（待七叔核对：如果你手上有 12306 原始邮件，可以翻一封确认正文里是否真的没有里程字样。我是从解析器覆盖范围倒推的，不如直接看邮件可靠。）

**时长**：`arrivalTime` 在解析器里是写死 `null` 的（`ChinaRailwayEmailParser.kt:394`），12306 通知只给发车时间。没有到达时间就算不出时长。

### 3.3 票款总额：会算错，要么不做要么说清口径

`totalPrice` 有两个问题：

1. **它是订单级的，而且会在同订单的多个行程上重复出现。** 解析器把「票款共计 X 元」写进 `Reservation.totalPrice`（`ChinaRailwayEmailParser.kt:283`、`386`），而 `toDocuments()` 会把同一订单按不同车次/时间拆成多个 `TravelDocument`，每个都带着同一个 `totalPrice`（`fromDocuments()` 里 `order.totalPrice` 被逐条下发，`ChinaRailwayEmailParser.kt:419-425`）。**直接按行程求和就是把一张订单的钱算好几遍。**

   正确做法是按 `reservation.reference` 去重后再求和。

2. **改签和退票的语义不同。** 购票和改签取的是「票款共计」，退票取的是「应退票款」（`ChinaRailwayEmailParser.kt:283-284`），两者放在一起加没有意义。

**建议：第一版不做金额统计。** 如果确实想要，就做成「已记录行程的票款合计（按订单去重）」，并在页面上明确标注口径，别叫「总花费」。

另一条路是用单张票价 `segments[0].attributes["price"]`（`ChinaRailwayEmailParser.kt:376-379`），它更细粒度、不会重复计算，但**不在表列里，只在 payload 中**，同样需要解码后才能聚合。

### 3.4 「出行次数」的口径要定死

这是最容易做错的地方。`archived = 1` 的行程其实混了三类东西（归档条件见 `TravelDocumentRepository.kt:78`）：

- 正常走完、自动或手动归档的行程
- 改签后保留的**原行程**（`status = RESCHEDULED`）
- 退票的行程（`status = REFUNDED`）

**直接 `count(archived = 1)` 会把改签前的旧车票也算成一次出行，数字虚高。**

建议口径：`status == CONFIRMED` 且 `departureTime <= now`，才算一次出行。按 `Asia/Shanghai` 划分年/月边界，不跟随设备时区——否则用户出国跨时区后历史统计会整体漂移。

---

## 四、同类产品怎么做的

| 产品 | 做法 | 对我们的启示 |
|---|---|---|
| 航旅纵横 | 「行程统计」放在「我」里，按年份查，配飞行员里程和航线图 | 统计放二级页面，不动首页 |
| 铁路伴侣（12306 生态） | 「我的足迹」自动记录每次出行，动态统计乘车次数和里程，可一键分享 | 这是和我们最像的对标：铁路场景下用户确实想看累计乘车次数 |
| Google Wallet / TripIt | 重点是「下一趟行程」卡片置顶，统计很轻 | 「即将出发」应该是首页第一屏的主角，统计是配角 |

两点判断：

- 车票类应用里，「即将出发」的价值高于统计。用户打开应用的第一个动机永远是「我下一趟什么时候走」。
- 「出行次数」属于回顾型信息，放首页没问题，但不要抢走第一屏。铁路伴侣把它单独放「足迹」页是有道理的。

---

## 五、导航改造方案

### 5.1 为什么要 3 个 tab 而不是 2 个

Material Design 3 的 NavigationBar 指南原文要求 3～5 个目的地，并明确写了「Don't use a navigation bar for fewer than three destinations. Instead, use tabs」，同时要求标签文字 1～2 个词、选中项用实心图标。

现在「设置」和「历史行程」都藏在首页右上角菜单里。加上 Dashboard 后如果只做 2 个 tab，既不合规，又浪费了这次机会。

建议结构：

```text
底栏：[ 首页 ]  [ 行程 ]  [ 设置 ]

首页     Dashboard：即将出发 + 出行统计
行程     未出发列表（现在的 HomeScreen）；顶部页内切换「未出发 / 历史」
设置     现在的 SettingsScreen，从溢出菜单提上来
```

「历史行程」不再单独占一个 tab。MD3 的说法是「用导航切换不同页面，用 tabs 切换同一页面内的相关内容」——未出发和历史是同一种内容的两个视图，属于后者的定义，放 `TabRow` 或 `SegmentedButton` 更贴切。

新增页面（导入、确认、邮箱配置）仍然走现有的二级页面机制，全屏推入，底栏隐藏。

### 5.2 沿用现有手写导航，不引入 navigation-compose

理由：

- 项目对依赖很克制。`gradle/libs.versions.toml` 里总共 14 个库，都是必需项（Room、Compose、ML Kit、JavaMail、Play services）。Navigation Compose 会带来一个不算小的新依赖，以及 `build.gradle.kts` 的改动。
- 现有 `Screen` 枚举 + `AnimatedContent` 这套写法虽然朴素，但 8 个页面的规模完全撑得住，而且已经处理了返回栈和转场方向。
- Dashboard 不需要深链接、不需要带参路由，Navigation Compose 的核心优势用不上。

具体改动：加一个 `MainTab` 枚举（`DASHBOARD` / `TRIPS` / `SETTINGS`）表示底栏选中项，跟现有 `Screen` 是两套状态——`Screen` 管二级页面的推入，`MainTab` 管顶层切换。两者在 App 层组合。

### 5.3 两个必须处理的细节

**转场动画要分开处理。** 现在的 `AnimatedContent` 对所有页面统一做水平滑动（`TravelWalletApp.kt:284-292`）。tab 之间切换如果也用水平滑动，用户会以为发生了层级导航。MD3 明确要求 tab 之间用交叉淡入、不要用横向位移。所以 tab 切换要单独走淡入淡出，二级页面推入才用滑动。

**FAB 要挪到外层 Scaffold。** 现在 FAB 挂在 `HomeScreen` 自己的 `Scaffold` 里（`TravelWalletApp.kt:659-663`）。加底栏后建议把 FAB 提到外层 `Scaffold`，由当前 tab 决定是否显示，避免嵌套 Scaffold 的 padding 互相打架导致内容被底栏遮住。

---

## 六、六个真实的坑

按影响从大到小：

1. **历史数据可能是空的。** 用户首次同步若选了「仅同步未出发」，本地没有历史行程，Dashboard 会显示 0。必须在 Dashboard 上做「导入历史行程」的入口或引导，否则功能上线即被认为坏了。

2. **历史统计会被后续邮件改写。** 退票邮件到达时，`replaceReservations` 会按订单全量替换，把一趟**已经坐完**的行程 `status` 改成 `REFUNDED` 并归档（`TravelDocumentRepository.kt:36-83`）。于是去年那趟车的出行次数在今年被减掉一次。这是数据模型层面的固有行为，不是 bug，但 Dashboard 上会表现为「数字莫名其妙变少了」。要么接受，要么在统计里额外区分「已改签/已退票」让用户看得见原因。

3. **删除行程会直接减少统计。** `deleteById` 是硬删除，没有墓碑（`TravelDocumentDao.kt:48-49`）。如果希望统计相对稳定，可以考虑软删除。这个要权衡，加列意味着又一次 Room 迁移。

4. **`autoArchiveDepartedTrips` 关闭时，首页会残留已出发的行程。** 是否自动归档是用户开关（`AppPreferences.kt:102-106`）。关闭时，一趟车到点后仍然留在 `archived = 0` 里。所以「即将出发」模块不能直接取列表首条，要过滤掉已过出发时间的。

5. **时区口径不一致。** 统计建议固定 `Asia/Shanghai`，但现有 UI 格式化用 `ZoneId.systemDefault()`（`TravelWalletApp.kt:151-153`）。两处不一致会让「今天」「本月」的边界在用户出国时出现偏差。建议统一，但统一到哪边要定。

6. **倒计时需要自建 ticker。** 现有代码没有任何每秒/每分钟刷新的机制。倒计时要么用 `LaunchedEffect` + `delay` 循环，要么在跨过整数分钟时才更新（省电，且对分钟级倒计时足够）。建议后者。

---

## 七、决策记录

以下为 2026-09-14 与七叔确认的结论。

### 已拍板

| # | 事项 | 结论 |
|---|---|---|
| 1 | 底栏结构 | **3 项**：首页 / 行程 / 设置。「设置」从首页三点菜单提到底栏，「历史行程」降为「行程」页内的切换 |
| 2 | 第一版指标范围 | **即将出发 + 发车倒计时**、**出行次数（总 / 今年 / 本月）**、**常坐线路 Top 3**。年度趋势折线图推迟到第二版 |
| 3 | 历史行程引导 | **Dashboard 空状态放「导入历史行程」入口**，复用现有 `loadFromEmail(includeHistoricalTrips = true)` |
| 4 | 里程 | 第一版不做（未列入范围），理由见 3.2 |
| 5 | 票款金额 | 第一版不做（未列入范围），理由见 3.3 |

七叔未对趋势图和席别分布表态，按「第二版」处理，第一版不排期。

### 按调研建议执行，如有异议请指出

| # | 事项 | 处理方式 |
|---|---|---|
| 6 | 是否引入 navigation-compose | 不引入，沿用现有手写导航 + 新增 `MainTab` 枚举，理由见 5.2 |
| 7 | 聚合放在哪一层 | 放 `core` 纯函数，不动 Room 表结构，理由见 5.2 与第六节 |
| 8 | 删除是否改软删除 | 不改，保持硬删除，接受统计数字会随删除变化 |
| 9 | 出行次数口径 | `status == CONFIRMED` 且已过出发时间，年月按 `Asia/Shanghai` 划分 |
| 10 | README / 网站文案同步 | 必须更新。`README.md:52` 写了「在首页查看未来行程和乘车关键信息」，`site/index.html:56` 写了「首页展示有效行程」，加 Dashboard 后首页的定义变了 |

> 更正：本文档初稿写「`AGENTS.md` 和 README 都写了」，实际上 `AGENTS.md` 里没有「首页」相关表述（全文 grep 无命中）。核对后 `AGENTS.md` 的产品范围一节补充了底栏结构和统计口径两条约定。

---

## 八、实施拆分

1. **`core` 模块新增纯函数聚合层。** 输入 `List<TravelDocument>` + 当前时刻 + 时区，输出 `TravelSummary`（总次数、今年、本月、常坐线路 Top N）。配单元测试进 `:core:test`，沿用现有 `ChinaRailwayEmailParserTest` 的写法。
2. **App 层加底栏。** 新增 `MainTab` 枚举（`DASHBOARD` / `TRIPS` / `SETTINGS`），与现有 `Screen` 组合；`NavigationBar` 和外层 `Scaffold` 放在 App 层；FAB 提到外层；tab 之间改淡入淡出，二级页面保留水平滑动。
3. **重排入口。** 「设置」移到第三栏，`SettingsScreen` 的返回逻辑改为回首页 tab；`HomeScreen` 改名并承担「行程」页职责，顶部加「未出发 / 历史」页内切换，替换掉原来的 `ArchiveScreen` 独立页面。
4. **实现 Dashboard。** 即将出发卡片（含倒计时 ticker，每 1 分钟刷新）+ 出行次数三张卡 + 常坐线路 Top 3。
5. **补空状态。** 无行程时给「导入历史行程」入口；历史为空但有待确认邮件时优先引导确认。
6. **更新文档。** `AGENTS.md`、README、CHANGELOG、`site/` 页面。

第 1 步是纯逻辑、可测、无 UI 依赖，风险最低，适合先落地。2、3 步是一次性的结构改造，建议合并成一个改动，避免中间态出现两套导航。

---

## 九、实现状态

分支 `feature/dashboard`，已完成：

| 步骤 | 产出 |
|---|---|
| 1 | `core/src/main/kotlin/com/neko7ina/wallet/assistant/core/summary/TripStatistics.kt`，含 `TravelSummary`、`RouteStat` 和 `summarize()`；配套测试 `core/src/test/kotlin/.../summary/TripStatisticsTest.kt` |
| 2 | `TravelWalletApp.kt` 新增 `MainTab`、`TripsView` 和 `MainTabsScaffold`，`Screen` 收敛为只有二级页面；FAB 提到外层 Scaffold；标签页用淡入淡出 |
| 3 | `TripsScreen` 取代 `HomeScreen` 与 `ArchiveScreen`，顶部用 `TabRow` 切换未出发与历史，内容用 `HorizontalPager` 承载，可左右滑动翻页 |
| 4 | `DashboardScreen` 含 `NextTripCard`、`TripCountCards`、`TopRoutesCard`；空状态为 `DashboardEmptyState`，另有 `HistoryImportHintCard` |
| 5 | `AGENTS.md` 补两条约定，README 新增「一眼看到出行概况」一节，`site/index.html` 增卡片，CHANGELOG 填 [未发布] |

统计口径按第七节执行：`status == CONFIRMED` 且已过出发时间，年月按 `Asia/Shanghai`。倒计时由 `rememberMinuteTicker()` 驱动，对齐整分钟。

实现过程中确认的两点：

- `SettingsScreen` 从二级页面变成顶层标签页后，它的 `Scaffold` 会和外层 `Scaffold` 的 window insets 叠加，因此改为 `Column` + 顶栏 `windowInsets = WindowInsets(0, 0, 0, 0)`。`TripsScreen` 和 `DashboardScreen` 同样处理。
- 原「自动同步已开启，新行程会显示在首页」的提示语已改为「行程页」，因为首页现在不再是行程列表。

### 归档手势改成「长按卡片弹菜单」

历史从二级入口变成与未出发平级的标签页之后，左右滑动自然要让给页面切换。而未出发卡片原来用的是 **左滑归档**（`SwipeToDismissBox`，`EndToStart`），和「左滑翻到历史页」是同一个手势方向。

这在 Compose 里没有折中余地：`SwipeToDismissBox` 内部是 `anchoredDraggable`，子级会先拿到横向拖拽并消费掉，父级的 `HorizontalPager` 根本收不到事件。两个都挂在左滑上，必然废掉一个。

七叔拍板：**归档改为长按未出发卡片弹出菜单**，横向手势完全交给翻页；菜单结构留白，以后别的操作也往这里放。

实现要点：

- 删掉 `SwipeToArchiveTripCard` 和 `SwipeToDismissBox` / `SwipeToDismissBoxValue` / `rememberSwipeToDismissBoxState` 三个导入。
- 新增 `UpcomingTripCard`：`Box` 包 `CompactTripCard` + `DropdownMenu`，菜单项「归档」。
- `CompactTripCard` 加 `onLongClick: (() -> Unit)? = null`，`Card(onClick = ...)` 换成 `Card` + `Modifier.clip(CardDefaults.shape).combinedClickable(...)`。**注意点击涟漪要自己 clip**，`Card(onClick)` 那套自带裁剪，换成 `combinedClickable` 后 `modifier` 排在 `Surface` 的裁剪之前，不补 `clip` 的话波纹是方的。
- `TripsScreen` 里的手势同步：`rememberPagerState(initialPage = view.ordinal) { TripsView.entries.size }`；`LaunchedEffect(pagerState.currentPage)` 把翻页结果同步给标签栏（用 `currentPage` 而非 `settledPage`，翻到一半标签就跟着走）；`LaunchedEffect(view)` 在点击标签时 `animateScrollToPage`，但用 `!pagerState.isScrollInProgress` 护栏避免和手指抢方向。

### 复核时修掉的三处

1. `hasDeparted()` 内部是 `segments.minOf { ... }`，对空 segments 会抛 `NoSuchElementException`。首页一打开就要遍历整个列表，所以在 `DashboardScreen` 里先判 `segments.isNotEmpty()`。
2. 原来无论有没有出行记录都渲染三张次数卡片，新用户会看到「总出行 0 次 / 今年 0 次 / 本月 0 次」挨着待确认提示，像是坏了。改为只在 `summary.hasTrips` 时显示卡片，否则改为历史导入提示。
3. `SettingsScreen` 没有 FAB，底部内边距从 88dp 收到 16dp。

### 构建验证

| 命令 | 结果 |
|---|---|
| `:core:test` | `TripStatisticsTest` 11 个用例，0 失败 0 错误 |
| `:app:assembleDebug` | BUILD SUCCESSFUL，`app-debug.apk` 约 63.6 MB |
| `:app:assembleRelease` | BUILD SUCCESSFUL，R8 混淆与 lintVital 均通过，`app-release.apk` 约 45.6 MB |

release 包签名 `CN=WalletAssistant, O=NeKo7inA, C=CN`，证书 SHA-256 `eafaba2f329a58d091d1641630339f2cc3e81bb72c4e3e048fe5f068fe2be2be`。只读拉取真机上已安装的 `base.apk` 核对，**签名摘要完全一致**，所以 `adb install -r` 可以原地覆盖升级，不会清掉设备上的行程数据和邮箱配置。

## 十、真机验收记录

设备 Pixel 10 Pro（`59271FDCH002F9`），2026-09-14 16:42 覆盖安装 release 包。

### 通过项

| 项 | 证据 |
|---|---|
| 原地升级不丢数据 | `firstInstallTime` 仍是 2026-08-26，`lastUpdateTime` 更新为 16:42:35，说明是覆盖安装而非重装 |
| 历史数据完好 | 首页显示「总出行 62 次 / 今年 2 次 / 本月 0 次 / 走过 16 座车站」，常坐线路「上海站 → 镇江站 14 次」等，与设备上原有记录相符 |
| 首页渲染 | 次数卡片与常坐线路卡片正常，无布局错位 |
| 底栏三项 | 首页 / 行程 / 设置 三项齐全，选中态正常 |
| 冷启动落点 | `am force-stop` 后 `am start -W` 冷启动落在首页 |
| 无崩溃 | `logcat -b crash` 为空 |

注意 `install -r` 之后第一次 `am start` 会由系统恢复上次的任务状态，可能落在上次停留的标签页（这次落在了行程页），测冷启动落点必须先 `force-stop`。

### 记录的两点

1. **首页「即将出发」在无未出发行程时整块消失。** 有历史记录（`summary.hasTrips == true`）但 `nextTrip == null` 时，`LazyColumn` 只渲染次数卡和线路卡，倒计时区没有任何占位文案，视觉上留大片空白。已补 `NoUpcomingTripCard`（「暂无即将出发的行程」），条件为 `nextTrip == null && !hasNothingSaved`，后者保证「全新用户 + 待确认邮件」那条路径不会多出这张卡。
2. **常坐线路会把同一线路拆成两条。** 设备上出现「镇江站 → 上海站 13 次」和「镇江 → 上海 11 次」，是 12306 邮件里站名在 2020 年前后改了写法导致的。**已修**，见第十二节。

### 未完成

行程页 `TabRow`（未出发 / 历史）与设置页（顶栏无返回箭头、从邮箱配置返回落在设置页）需要点击操作，验收时设备正被另一个会话（SevenMirror `dev.notificationmirroring.android`）并发驱动，两边都会用 `adb shell input tap`，继续盲点会互相打断，故停手。待设备空闲再补。

## 十一、真机验收清单

七叔于 2026-09-15 将设备接入本机，安装预览包后逐项体验，确认通过，随后合并并发布 `v1.1.0`。以下清单保留，供后续回归时参照。

**动手前先做并发检查**：无输入采两次截图比对哈希，若不同说明有别的会话在驱动设备，停手。详见设备验收技能里的「并发占用检查」。

| # | 要验的 | 怎么看 |
|---|---|---|
| 1 | 首页无未出发行程时的占位卡 | 当前设备正好是「有历史、无未出发」，装上就能看到「暂无即将出发的行程」，不会再留空白 |
| 2 | 行程页左右滑动翻页 | 在列表上横向拖，未出发 ↔ 历史 应跟手；标签栏在拖过一半时跟着变 |
| 3 | 翻页动画 | 滑动本身是 pager 的自然动画，松手后应带惯性吸附到整页；点标签则是平滑滚动过去 |
| 4 | 长按卡片弹菜单 | 长按未出发卡片 → 弹出「归档」菜单；点归档后该行程进历史页 |
| 5 | 长按不误触 | 长按后手指抬起不应同时触发「打开详情」 |
| 6 | 列表纵向滚动未被劫持 | 上下滚行程列表应正常，不会变成翻页 |
| 7 | 设置页 | 顶栏无返回箭头；从设置进邮箱配置再退出，应回到设置标签页 |
| 8 | 冷启动落点 | 先 `am force-stop` 再 `am start -W`，应落在首页 |
| 9 | 无崩溃 | `adb logcat -b crash` 为空 |

顺带能一起看的（本轮已知问题，不是回归）：

- 常坐线路里「镇江站 → 上海站」和「镇江 → 上海」应合并成一条，并按去掉「站」的形式展示成「镇江 → 上海」；「走过 N 座车站」的 N 应该比之前小（上海 / 上海站、镇江 / 镇江站 各多算了一座）。
- 首页倒计时需要等一分钟观察 `rememberMinuteTicker()` 是否真的对齐整分钟刷新。

## 十二、车站名归一化

### 问题

12306 邮件在 2020 年前后改了车站名的写法：老邮件不写「站」（「镇江」），新邮件写（「镇江站」），指的是同一个车站。设备上因此出现「上海站 → 镇江站 14 次」「镇江站 → 上海站 13 次」「镇江 → 上海 11 次」三条并存的线路。

站名来自 `TICKET_REGEX`（`ChinaRailwayEmailParser.kt:287`）的第 7、8 组，是**原样截取邮件正文**，中间没有任何处理，所以邮件怎么写就怎么入库。

影响不止常坐线路：

- `TripStatistics` 的线路分组把同一线路拆成两条
- **`visitedStationCount` 被撑大**。它把起终点名丢进 `Set` 去重，上海 / 上海站、镇江 / 镇江站各算一座，首页那个「走过 16 座车站」至少多算了 2
- 列表、通知、Google Wallet 卡片的文案不统一
- `ChinaRailwayEmailParser.kt:92-95` 的改签候选筛选用 `origin == origin && destination == destination`，跨年份的改签单理论上会失配（有 `travelerCandidates.size == 1` 兜底，风险低）

### 为什么不能在录入或读取时归一

一开始想在解析层做，查下来会踩到身份计算：

```
RailwayTicket.journeyKey = 出发时间 | 起点 | 终点 | 车次      (parser:335-340)
        ↓
TravelDocument.stableId() = SHA-256(provider + 订单号 + journeyKey)   (TravelDocumentId.kt:6)
```

`stableId()` 是本地行程主键，同时是这些地方的 key：

- `TripReminderScheduler` 的 Alarm ID
- `TripAutoArchiveScheduler` 的自动归档任务 ID
- `GoogleWalletPassFactory:24` 的 pass `objectId`
- `replaceReservations` 判断「新行程还是更新」

而且要命的是 `MutableOrder.fromDocuments()`（parser:424-457）：**增量同步每次都会拿已保存行程的 `Location.name` 反算 `journeyKey`**，`EmailSyncCoordinator:78-82` 把 `repository.allDocuments()` 当基线喂进去，算完的结果还会落库。

现在「存进去再读出来算一遍」之所以稳定，正是因为 `Location.name` 存的是邮件原文。**一旦在解析层或反序列化时改动它，`journeyKey` 就变，`stableId()` 跟着变，设备上已有的行程主键会全部失配 —— 下次同步变成重复插入，提醒和归档任务、Wallet pass 一起孤立。**

### 做法

**存储保留邮件原文，归一化只发生在展示层和统计层。**

- 新增 `core/.../model/RailStationNames.kt`：`RailStationNames.normalize()` 去掉末尾的「站」，本来就不带「站」的原样返回，所以可重复调用；配套 `Location.railStationName` 和 `TravelSegment.railRoute` 两个扩展属性。
- 方向选**去「站」**（七叔定；2026-09-15 从原先的「补站」改过来）：邮件里带不带「站」都不动，统一在展示时去掉。卡片字段本来就写着「出发站 / 目的站」，名字里再带一个「站」是重复的；去掉之后也跟历史数据的主流写法一致。
- 落点：`TripStatistics`（分组 + 去重前归一，计数字段相应改名为 `visitedStationCount`）、`TravelWalletApp` 的 4 处行程文案、`TripReminderReceiver` 的通知路由、`GoogleWalletPassFactory` 的 header 与「出发站 / 目的站」字段。
- `ChinaRailwayEmailParser.kt:435-436` 保持读 `Location.name` **原文**，并加了注释说明原因 —— 那里是 `journeyKey` 的重建点，归一化就是改主键。
- 已知取舍：只按后缀剥离，不识别车站实体。若某个车站名本身就以「站」字结尾而非「地名＋站」（极罕见），也会被剥掉；名字恰好写作「站」时保留原文，避免渲染成空串。

### 验证

| 命令 | 结果 |
|---|---|
| `:core:test` | 26 个用例，0 失败 0 错误（`RailStationNamesTest` 8、`TripStatisticsTest` 13、`ChinaRailwayEmailParserTest` 5） |

`ChinaRailwayEmailParserTest` 仍断言 `assertEquals("苹果站", segment.origin.name)` 并通过，说明**解析输出没变**，主键不受影响。

新增用例：`站名带不带「站」的同一线路合并统计`（镇江 / 上海 与 镇江站 / 上海站 合并为 2 次）、`多段行程的首末站名都走归一`。

## 十三、发布 v1.1.0

| 项 | 值 |
|---|---|
| 版本 | `1.1.0`（versionCode 3） |
| 分支 | `feature/dashboard` → `main`（fast-forward，7 个提交） |
| Tag | `v1.1.0`（annotated，与 `v1.0.0` / `v1.0.1` 同格式） |
| 发布方式 | 推送 tag 触发 `.github/workflows/release.yml`，自动测试 → 签名构建 → 验签 → 建 Release |
| 产物 | `chuxing-1.1.0.apk` 与 `chuxing-1.1.0.apk.sha256` |

版本号按语义化版本走 minor：本次新增出行概况首页、三项底栏与页内滑动切换，属新功能而非修补。工作流会校验 `versionName` 与 tag 是否严格一致（`grep -Fq "versionName = \"${version}\"" app/build.gradle.kts`），两处必须同步改。

本地验证：`:core:test` 26 例 0 失败；`:app:assembleRelease` BUILD SUCCESSFUL，43.51 MB，`aapt2 dump badging` 确认 `versionCode='3' versionName='1.1.0'`，签名 `eafaba2f…fe2be2be` 与工作流中写死的期望证书一致。

### 官网首页展示图

`site/index.html` 在 hero 之后新增「一眼看到出行概况」展示区，配真机首页截图 `site/assets/screenshot-dashboard.png`（1080×2109，98.7 KB）。

裁切范围取 `y=175 ~ 2284`，两侧边界都是算出来的而不是目测的：

- **上边界 175**：按像素扫描，状态栏内容占 `69~106`，TopAppBar 的「首页」标题文字占 `231~286`。标题中心 258.5，而 Material 3 `TopAppBar` 高 64dp（2.625 倍率下 168px），反推顶栏顶部 = 258.5 − 84 = 174.5，即状态栏底边。
- **下边界 2284**：`y=2074` 起行主色从内容背景 `(14,14,15)` 变为 `(25,25,27)`，这是 NavigationBar 背景，高度 `2284 − 2074 = 210px`，正好是 80dp；`y=2284` 起再变为 `(26,26,28)`，即系统手势导航区。

所以裁掉的是纯系统 UI，三项底栏完整保留。原始截图留在 `.device-verify/`（该目录已进 `.gitignore`，不进仓库）。

试算过缩放版本，720 宽的反而比 1080 原尺寸更大（113 KB vs 101 KB）—— LANCZOS 重采样引入了额外色彩，PNG 压缩率下降，因此保留全分辨率。

## 十四、发布 v1.2.0

| 项 | 值 |
|---|---|
| 版本 | `1.2.0`（versionCode 4） |
| 分支 | `feature/auto-save-synced-trips` → `main`（fast-forward） |
| Tag | `v1.2.0`（annotated，与 `v1.0.0` / `v1.0.1` / `v1.1.0` 同格式） |
| 发布方式 | 推送 tag 触发 `.github/workflows/release.yml` |
| 产物 | `chuxing-1.2.0.apk` 与 `chuxing-1.2.0.apk.sha256` |

版本号按语义化版本走 minor：把「解析后等用户确认」改成「解析后直接落库」，是行为变更而非修补。

### 为什么要改

七叔买票后自动同步扫到了，但弹出通知要他手动保存。他期望的是「扫到就自动进行程，最多通知告知一声，下次打开就能看到」。旧策略下候选先存进 `pending_email_import` 等确认，**确认前不推进 IMAP 位置**，期间所有同步都会被 `sync()` 开头那句提前返回挡下来 —— 一张没处理的票会让自动同步一直停摆。

### 实现要点

- 新增 `TravelDocumentImporter`，把「取消旧提醒与归档任务 → `replaceReservations` 落库 → 重排提醒与自动归档」这套动作从应用内导入路径抽出来，后台 worker 与手动同步共用同一份。
- `EmailSyncCoordinator` 删掉 `PendingConfirmation` 分支，`ParseResult.Success` 且非空就直接 `persist`，返回新的 `Imported(documents, warnings)`。
- **先落库、后推进 IMAP 位置。** 顺序反过来的话，中途失败会真把那几封邮件漏掉；这个顺序下失败只是下次重扫，而落库按行程主键覆盖本身幂等。
- 旧版遗留的 `pending_email_import` 在应用启动和每次同步开头各补存一次（`flushLeftoverPendingImport`），免得升级后那张票永远卡在待确认。
- `AutomaticEmailSyncStatus` 去掉 `PENDING_CONFIRMATION` 枚举值。getter 走 `runCatching` + 默认 NEVER，所以旧偏好里残留的那个字符串不会让应用崩。
- 通知由 `EmailSyncNotification.showImportedTrips` 发，点开带 `EXTRA_OPEN_UPCOMING_TRIPS`，`TravelWalletApp` 收到后切到行程页「未出发」。

### 真机验收

Pixel 10 Pro `59271FDCH002F9`，装 `versionCode=4 / versionName=1.2.0`，签名 `eafaba2f…fe2be2be`，与设备上原包同证书，`install -r` 数据完整（78 条行程、邮箱配置、偏好都在）。

造「全新购票邮件」场景没有动数据库文件：先用应用自己的「删除行程」（硬删 `dao.deleteById`）删掉 `E842237123`，再把 `trip_reminders.xml` 的 `lastScannedUid` 从 85 回退到 83。

结果：worker 自动执行（logcat `Starting work for AutomaticEmailSyncWorker` → `Worker result SUCCESS`），行程自己回来，`updatedAt` 正好是执行时刻，`remind=1 / archived=0 / CONFIRMED`，**全程零人工操作**；`pending_email_import` 仍 0 行；检查点推进到 85。通知显示「出行 · 已添加 2 条行程 / 镇江 → 上海 等」，点开直接落到行程页「未出发」。手动同步结果页也确认过：标题「已按订单更新本地记录，以下是本次同步的行程。」，按钮「完成」。

本地验证：`:core:test` 26 例 0 失败；`:app:assembleRelease` BUILD SUCCESSFUL，45.63 MB，`aapt2` 确认 `versionCode='4' versionName='1.2.0'`。

### 已知的文案边界

`notificationTitle` 只按「本批是否全为 `CONFIRMED`」决定说「已添加」还是「已更新」，不判断行程是否本来就存在。真实链路里 worker 只扫新邮件，所以买新票一定是「已添加」，改签退票走「已更新」，是对的。只有当同一封购票通知被重复扫到（本次验收人为回退检查点造成的）才会把老行程也说成「已添加」。

