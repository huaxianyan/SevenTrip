package com.neko7ina.wallet.assistant

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.railRoute
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import com.neko7ina.wallet.assistant.core.parser.normalizeOcrTextForStructuredParsing
import com.neko7ina.wallet.assistant.core.summary.TravelSummary
import com.neko7ina.wallet.assistant.core.summary.TripStatistics
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.email.EmailSyncProgress
import com.neko7ina.wallet.assistant.email.ImapAccountConfig
import com.neko7ina.wallet.assistant.email.ImapAccountSummary
import com.neko7ina.wallet.assistant.email.ImapProviderPreset
import com.neko7ina.wallet.assistant.screenshot.ScreenshotRecognitionResult
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncInterval
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncStatus
import com.neko7ina.wallet.assistant.settings.ReminderTimingConstraints
import com.neko7ina.wallet.assistant.settings.ThemeMode
import com.neko7ina.wallet.assistant.wallet.GoogleWalletPassFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** 底栏承载的顶层页面。 */
private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(
        label = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    TRIPS(
        label = "行程",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
    ),
    SETTINGS(
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

/** 「行程」页内切换未出发行程和历史行程。 */
private enum class TripsView(val label: String) {
    UPCOMING("未出发"),
    HISTORY("历史"),
}

/**
 * 需要全屏推入的二级页面。顶层页面由 [MainTab] 承载，
 * 这里只保留会盖住底栏的页面。
 */
private enum class Screen {
    MAIN,
    EMAIL_IMPORT,
    EMAIL_ACCOUNT,
    TEXT_IMPORT,
    SCREENSHOT_IMPORT,
    CONFIRM,
}

private enum class ImportMode {
    EMAIL,
    SCREENSHOT,
}

private val Screen.depth: Int
    get() = when (this) {
        Screen.MAIN -> 0
        Screen.CONFIRM,
        Screen.EMAIL_ACCOUNT,
        -> 2
        else -> 1
    }

private fun formatSyncTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm"))

private fun AutomaticEmailSyncInterval.displayName(): String = when (this) {
    AutomaticEmailSyncInterval.ONE_HOUR -> "每 1 小时"
    AutomaticEmailSyncInterval.THREE_HOURS -> "每 3 小时"
    AutomaticEmailSyncInterval.SIX_HOURS -> "每 6 小时"
    AutomaticEmailSyncInterval.TWELVE_HOURS -> "每 12 小时"
    AutomaticEmailSyncInterval.TWENTY_FOUR_HOURS -> "每 24 小时"
}

private enum class WalletAvailability {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWalletApp(
    checkGoogleWalletAvailability: ((Boolean) -> Unit) -> Unit,
    addToGoogleWallet: (String) -> Unit,
    requestScreenshotRecognition: ((ScreenshotRecognitionResult) -> Unit) -> Unit,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    requestExactReminderPermission: ((Boolean) -> Unit) -> Unit,
    openUpcomingTrips: Boolean,
    onUpcomingTripsOpened: () -> Unit,
    setDarkSystemBars: (Boolean) -> Unit,
    viewModel: TravelWalletViewModel = viewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val archivedDocuments by viewModel.archivedDocuments.collectAsStateWithLifecycle()
    val newTripsReminderEnabled by viewModel.newTripsReminderEnabled.collectAsStateWithLifecycle()
    val autoArchiveDepartedTrips by viewModel.autoArchiveDepartedTrips.collectAsStateWithLifecycle()
    val departureReminderMinutes by viewModel.departureReminderMinutes.collectAsStateWithLifecycle()
    val liveStatusMinutes by viewModel.liveStatusMinutes.collectAsStateWithLifecycle()
    val googleWalletActionVisible by viewModel.googleWalletActionVisible.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val emailImportState by viewModel.emailImportState.collectAsStateWithLifecycle()
    val emailAccountSummary by viewModel.emailAccountSummary.collectAsStateWithLifecycle()
    val emailAccountTestState by viewModel.emailAccountTestState.collectAsStateWithLifecycle()
    val emailFolderState by viewModel.emailFolderState.collectAsStateWithLifecycle()
    val automaticEmailSyncEnabled by viewModel.automaticEmailSyncEnabled.collectAsStateWithLifecycle()
    val automaticEmailSyncInterval by viewModel.automaticEmailSyncInterval.collectAsStateWithLifecycle()
    val automaticEmailSyncStatus by viewModel.automaticEmailSyncStatus.collectAsStateWithLifecycle()
    val automaticEmailSyncStatusAt by viewModel.automaticEmailSyncStatusAt.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var tab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    var tripsView by rememberSaveable { mutableStateOf(TripsView.UPCOMING) }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var importMode by rememberSaveable { mutableStateOf(ImportMode.EMAIL) }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var parsedDocuments by remember { mutableStateOf(emptyList<TravelDocument>()) }
    var confirmationSource by rememberSaveable { mutableStateOf(Screen.TEXT_IMPORT) }
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTrip = (documents + archivedDocuments).firstOrNull {
        it.document.stableId() == selectedTripId
    }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showEmailDisclosure by rememberSaveable { mutableStateOf(false) }
    var showEmailHistoryChoice by rememberSaveable { mutableStateOf(false) }
    var walletAvailability by remember { mutableStateOf(WalletAvailability.CHECKING) }
    val walletPassFactory = remember { GoogleWalletPassFactory() }
    val context = LocalContext.current
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 && useDarkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        useDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    fun changeReminder(enabled: Boolean, apply: () -> Unit) {
        if (!enabled) {
            apply()
            return
        }
        requestNotificationPermission { notificationGranted ->
            if (!notificationGranted) {
                Toast.makeText(context, "请允许通知后再开启乘车提醒。", Toast.LENGTH_LONG).show()
                return@requestNotificationPermission
            }
            requestExactReminderPermission { exactReminderGranted ->
                if (exactReminderGranted) {
                    apply()
                } else {
                    Toast.makeText(context, "请允许精确提醒后再开启乘车提醒。", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun beginEmailImport() {
        if (viewModel.needsEmailHistoryChoice()) {
            showEmailHistoryChoice = true
        } else {
            screen = Screen.EMAIL_IMPORT
            viewModel.loadFromEmail()
        }
    }

    /** 邮箱配置是从设置页进入的二级页面，退出时回到设置标签页。 */
    fun closeEmailAccount() {
        tab = MainTab.SETTINGS
        screen = Screen.MAIN
    }

    // 自动同步已经把行程存好了，通知只是事后告知，点开就落到行程页。
    LaunchedEffect(openUpcomingTrips) {
        if (openUpcomingTrips) {
            screen = Screen.MAIN
            tab = MainTab.TRIPS
            tripsView = TripsView.UPCOMING
            onUpcomingTripsOpened()
        }
    }
    LaunchedEffect(useDarkTheme) {
        setDarkSystemBars(useDarkTheme)
    }
    LaunchedEffect(Unit) {
        checkGoogleWalletAvailability { available ->
            walletAvailability = if (available) WalletAvailability.AVAILABLE else WalletAvailability.UNAVAILABLE
        }
    }

    BackHandler(enabled = screen != Screen.MAIN || tab != MainTab.DASHBOARD) {
        if (screen == Screen.MAIN) {
            tab = MainTab.DASHBOARD
            return@BackHandler
        }
        screen = when (screen) {
            Screen.CONFIRM -> confirmationSource
            Screen.EMAIL_ACCOUNT -> {
                tab = MainTab.SETTINGS
                Screen.MAIN
            }

            else -> Screen.MAIN
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState.depth > initialState.depth) {
                        (fadeIn() + slideInHorizontally { width -> width }) togetherWith
                            (fadeOut() + slideOutHorizontally { width -> -width })
                    } else {
                        (fadeIn() + slideInHorizontally { width -> -width }) togetherWith
                            (fadeOut() + slideOutHorizontally { width -> width })
                    }
                },
                label = "page transition",
            ) { targetScreen ->
                when (targetScreen) {
                Screen.MAIN -> MainTabsScaffold(
                    tab = tab,
                    onTabChange = { entry ->
                        if (entry == MainTab.SETTINGS) {
                            viewModel.refreshAutomaticEmailSyncStatus()
                        }
                        tab = entry
                    },
                    onAddClick = { showAddSheet = true },
                    dashboard = {
                        DashboardScreen(
                            documents = documents,
                            archivedDocuments = archivedDocuments,
                            hasEmailAccount = emailAccountSummary != null,
                            onImportHistoryClick = {
                                screen = Screen.EMAIL_IMPORT
                                viewModel.loadFromEmail(includeHistoricalTrips = true)
                            },
                            onAddClick = { showAddSheet = true },
                            onTripClick = { selectedTripId = it.document.stableId() },
                        )
                    },
                    trips = {
                        TripsScreen(
                            view = tripsView,
                            onViewChange = { tripsView = it },
                            documents = documents,
                            archivedDocuments = archivedDocuments,
                            onTripClick = { selectedTripId = it.document.stableId() },
                            onArchive = { viewModel.setArchived(it, true) },
                        )
                    },
                    settings = {
                        SettingsScreen(
                            newTripsReminderEnabled = newTripsReminderEnabled,
                            autoArchiveDepartedTrips = autoArchiveDepartedTrips,
                            departureReminderMinutes = departureReminderMinutes,
                            liveStatusMinutes = liveStatusMinutes,
                            googleWalletActionVisible = googleWalletActionVisible,
                            themeMode = themeMode,
                            emailAccountSummary = emailAccountSummary,
                            automaticEmailSyncEnabled = automaticEmailSyncEnabled,
                            automaticEmailSyncInterval = automaticEmailSyncInterval,
                            automaticEmailSyncStatus = automaticEmailSyncStatus,
                            automaticEmailSyncStatusAt = automaticEmailSyncStatusAt,
                            onEmailAccountClick = {
                                viewModel.resetEmailAccountTestState()
                                screen = Screen.EMAIL_ACCOUNT
                            },
                            onNewTripsReminderChange = { enabled ->
                                changeReminder(enabled) {
                                    viewModel.setNewTripsReminderEnabled(enabled)
                                }
                            },
                            onAutomaticEmailSyncChange = { enabled ->
                                fun applyChange() {
                                    if (!viewModel.setAutomaticEmailSyncEnabled(enabled)) {
                                        Toast.makeText(
                                            context,
                                            "请先完成一次手动邮箱同步。",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                                if (enabled) {
                                    requestNotificationPermission { granted ->
                                        applyChange()
                                        if (!granted) {
                                            Toast.makeText(
                                                context,
                                                "自动同步已开启，新行程会显示在行程页。",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                } else {
                                    applyChange()
                                }
                            },
                            onAutomaticEmailSyncIntervalChange =
                                viewModel::setAutomaticEmailSyncInterval,
                            onAutoArchiveDepartedTripsChange =
                                viewModel::setAutoArchiveDepartedTrips,
                            onDepartureReminderMinutesChange =
                                viewModel::setDepartureReminderMinutes,
                            onLiveStatusMinutesChange = viewModel::setLiveStatusMinutes,
                            onGoogleWalletVisibilityChange =
                                viewModel::setGoogleWalletActionVisible,
                            onThemeModeChange = viewModel::setThemeMode,
                        )
                    },
                )

                Screen.EMAIL_IMPORT -> EmailImportScreen(
                    state = emailImportState,
                    onDone = { screen = Screen.MAIN },
                )

                Screen.EMAIL_ACCOUNT -> EmailAccountScreen(
                    existingAccount = emailAccountSummary,
                    testState = emailAccountTestState,
                    folderState = emailFolderState,
                    onBack = { closeEmailAccount() },
                    onSave = viewModel::testAndSaveEmailAccount,
                    onLoadFolders = viewModel::loadEmailFolders,
                    onFolderSelected = viewModel::selectEmailFolder,
                    onDelete = {
                        viewModel.deleteEmailAccount()
                        closeEmailAccount()
                    },
                )

                Screen.TEXT_IMPORT -> ImportScreen(
                    importMode = importMode,
                    emailBody = emailBody,
                    error = parseError,
                    onBodyChange = {
                        emailBody = it
                        parseError = null
                    },
                    onUseSample = if (BuildConfig.DEBUG && importMode == ImportMode.EMAIL) {
                        {
                            emailBody = createUpcomingReminderSample()
                            parseError = null
                        }
                    } else {
                        null
                    },
                    onBack = { screen = Screen.MAIN },
                    onParse = {
                        val textToParse = if (importMode == ImportMode.SCREENSHOT) {
                            normalizeOcrTextForStructuredParsing(emailBody)
                        } else {
                            emailBody
                        }
                        when (val result = ChinaRailwayEmailParser().parse(RawDocument(body = textToParse))) {
                            is ParseResult.Success -> {
                                parsedDocuments = result.documents
                                parseError = null
                                confirmationSource = Screen.TEXT_IMPORT
                                screen = Screen.CONFIRM
                            }

                            is ParseResult.Failure -> {
                                parseError = if (importMode == ImportMode.SCREENSHOT) {
                                    screenshotParseError(result.message)
                                } else {
                                    result.message
                                }
                            }
                        }
                    },
                )

                Screen.SCREENSHOT_IMPORT -> ScreenshotImportScreen(
                    onBack = { screen = Screen.MAIN },
                )

                Screen.CONFIRM -> ConfirmationScreen(
                    documents = parsedDocuments,
                    onBack = { screen = confirmationSource },
                    onSave = {
                        viewModel.save(documents = parsedDocuments)
                        screen = Screen.MAIN
                    },
                )
                }
            }
        }

        if (showAddSheet) {
            AddTripSheet(
                onDismiss = { showAddSheet = false },
                onEmailImport = {
                    showAddSheet = false
                    if (emailAccountSummary == null) {
                        showEmailDisclosure = true
                    } else {
                        beginEmailImport()
                    }
                },
                onTextImport = {
                    showAddSheet = false
                    importMode = ImportMode.EMAIL
                    emailBody = ""
                    parseError = null
                    screen = Screen.TEXT_IMPORT
                },
                onScreenshotImport = {
                    showAddSheet = false
                    requestScreenshotRecognition { result ->
                        when (result) {
                            ScreenshotRecognitionResult.Processing -> {
                                screen = Screen.SCREENSHOT_IMPORT
                            }
                            is ScreenshotRecognitionResult.Success -> {
                                if (screen != Screen.SCREENSHOT_IMPORT) return@requestScreenshotRecognition
                                importMode = ImportMode.SCREENSHOT
                                emailBody = result.text
                                parseError = null
                                screen = Screen.TEXT_IMPORT
                            }
                            ScreenshotRecognitionResult.Cancelled -> screen = Screen.MAIN
                            ScreenshotRecognitionResult.Failed -> {
                                screen = Screen.MAIN
                                Toast.makeText(
                                    context,
                                    "未能读取截图，请重新选择清晰图片或改用粘贴正文。",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                },
            )
        }

        if (showEmailDisclosure) {
            AlertDialog(
                onDismissRequest = { showEmailDisclosure = false },
                title = { Text("配置邮箱同步") },
                text = {
                    Text(
                        "邮箱授权码可以让「出行」读取邮箱中的邮件。请使用邮箱提供的专用密码或" +
                            "客户端授权码，不要填写邮箱登录密码。\n\n" +
                            "邮箱地址和授权码会加密保存在此设备上，仅用于在所选文件夹中查找来自 " +
                            "12306@rails.com.cn 的铁路订单通知。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmailDisclosure = false
                            viewModel.resetEmailAccountTestState()
                            screen = Screen.EMAIL_ACCOUNT
                        },
                    ) {
                        Text("继续配置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmailDisclosure = false }) {
                        Text("暂不配置")
                    }
                },
            )
        }

        if (showEmailHistoryChoice) {
            AlertDialog(
                onDismissRequest = { showEmailHistoryChoice = false },
                title = { Text("同步历史行程？") },
                text = {
                    Text(
                        "邮箱中可能包含已经结束的行程。你可以只同步未出发行程，" +
                            "或同时导入历史行程并直接归档。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmailHistoryChoice = false
                            screen = Screen.EMAIL_IMPORT
                            viewModel.loadFromEmail(includeHistoricalTrips = true)
                        },
                    ) {
                        Text("导入并归档历史")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showEmailHistoryChoice = false
                            screen = Screen.EMAIL_IMPORT
                            viewModel.loadFromEmail(includeHistoricalTrips = false)
                        },
                    ) {
                        Text("仅同步未出发")
                    }
                },
            )
        }

        selectedTrip?.let { saved ->
            TripDetailDialog(
                saved = saved,
                walletAvailability = walletAvailability,
                showGoogleWalletAction = googleWalletActionVisible,
                departureReminderMinutes = departureReminderMinutes,
                liveStatusMinutes = liveStatusMinutes,
                onDismiss = { selectedTripId = null },
                onReminderChange = { enabled ->
                    changeReminder(enabled) { viewModel.setReminderEnabled(saved, enabled) }
                },
                onTestReminder = if (BuildConfig.DEBUG && saved.reminderEnabled) {
                    {
                        viewModel.scheduleReminderTest(saved.document)
                        Toast.makeText(context, "测试提醒将在 10 秒后显示。", Toast.LENGTH_LONG).show()
                    }
                } else {
                    null
                },
                onAddToGoogleWallet = {
                    addToGoogleWallet(walletPassFactory.createUnsignedPass(saved.document))
                },
                onArchive = {
                    viewModel.setArchived(saved, true)
                    selectedTripId = null
                },
                onRestore = {
                    viewModel.setArchived(saved, false)
                    selectedTripId = null
                },
                onDelete = {
                    viewModel.delete(saved)
                    selectedTripId = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScaffold(
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    onAddClick: () -> Unit,
    dashboard: @Composable () -> Unit,
    trips: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    val selected = entry == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onTabChange(entry) },
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    entry.selectedIcon
                                } else {
                                    entry.unselectedIcon
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != MainTab.SETTINGS) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "添加行程")
                }
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "标签页切换",
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) { targetTab ->
            when (targetTab) {
                MainTab.DASHBOARD -> dashboard()
                MainTab.TRIPS -> trips()
                MainTab.SETTINGS -> settings()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    documents: List<SavedTravelDocument>,
    archivedDocuments: List<SavedTravelDocument>,
    hasEmailAccount: Boolean,
    onImportHistoryClick: () -> Unit,
    onAddClick: () -> Unit,
    onTripClick: (SavedTravelDocument) -> Unit,
) {
    val now = rememberMinuteTicker()
    val summary = remember(documents, archivedDocuments, now) {
        TripStatistics.summarize(
            documents = (documents + archivedDocuments).map { it.document },
            now = now,
        )
    }
    val nextTrip = documents.firstOrNull { saved ->
        saved.document.segments.isNotEmpty() && !saved.document.hasDeparted(now)
    }
    val hasNothingSaved = documents.isEmpty() && archivedDocuments.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("首页") }, windowInsets = WindowInsets(0, 0, 0, 0))
        if (hasNothingSaved) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DashboardEmptyState(
                    hasEmailAccount = hasEmailAccount,
                    onImportHistoryClick = onImportHistoryClick,
                    onAddClick = onAddClick,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val upcoming = nextTrip
                if (upcoming != null) {
                    item { NextTripCard(saved = upcoming, now = now, onClick = { onTripClick(upcoming) }) }
                } else if (!hasNothingSaved) {
                    item { NoUpcomingTripCard() }
                }
                if (summary.hasTrips) {
                    item { TripCountCards(summary) }
                    if (summary.topRoutes.isNotEmpty()) {
                        item { TopRoutesCard(summary) }
                    }
                } else if (hasEmailAccount) {
                    item { HistoryImportHintCard(onImportHistoryClick) }
                }
            }
        }
    }
}

@Composable
private fun DashboardEmptyState(
    hasEmailAccount: Boolean,
    onImportHistoryClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有行程", style = MaterialTheme.typography.headlineSmall)
        Text(
            "配置邮箱后可以导入铁路订单通知，已经结束的行程也可以一并导入并归档。",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hasEmailAccount) {
            Button(onClick = onImportHistoryClick, modifier = Modifier.fillMaxWidth()) {
                Text("导入历史行程")
            }
            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("添加行程")
            }
        } else {
            Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
                Text("添加行程")
            }
        }
    }
}

@Composable
private fun NextTripCard(
    saved: SavedTravelDocument,
    now: Instant,
    onClick: () -> Unit,
) {
    val segment = saved.document.segments.first()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("下一趟行程", style = MaterialTheme.typography.labelLarge)
            Text(
                segment.serviceNumber,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                segment.railRoute,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                segment.departureTime.format(DEPARTURE_FORMAT),
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                formatDepartureCountdown(segment.departureTime, now),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TripCountCards(summary: TravelSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TripCountCard("总出行", summary.totalTrips, Modifier.weight(1f))
        TripCountCard("今年", summary.tripsThisYear, Modifier.weight(1f))
        TripCountCard("本月", summary.tripsThisMonth, Modifier.weight(1f))
    }
}

@Composable
private fun TripCountCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$count 次",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TopRoutesCard(summary: TravelSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("常坐线路", style = MaterialTheme.typography.titleMedium)
                Text(
                    "走过 ${summary.visitedStationCount} 座车站",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary.topRoutes.forEach { route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${route.origin} → ${route.destination}")
                    Text(
                        "${route.count} 次",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoUpcomingTripCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("暂无即将出发的行程", style = MaterialTheme.typography.titleMedium)
            Text(
                "新行程保存后会显示在这里，并开始按发车时间倒计时。",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryImportHintCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("还没有出行记录", style = MaterialTheme.typography.titleMedium)
            Text(
                "统计只包含本地已保存的行程。如果邮箱里还有已经结束的行程，可以导入历史行程。",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsScreen(
    view: TripsView,
    onViewChange: (TripsView) -> Unit,
    documents: List<SavedTravelDocument>,
    archivedDocuments: List<SavedTravelDocument>,
    onTripClick: (SavedTravelDocument) -> Unit,
    onArchive: (SavedTravelDocument) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = view.ordinal) { TripsView.entries.size }

    // 滑动翻页 → 同步标签栏。用 currentPage 而不是 settledPage，翻到一半就能看到标签跟着走。
    LaunchedEffect(pagerState.currentPage) {
        TripsView.entries.getOrNull(pagerState.currentPage)?.let { target ->
            if (target != view) onViewChange(target)
        }
    }
    // 点标签 → 让翻页动画跟过去。拖动过程中不插手，否则会和手指抢方向。
    LaunchedEffect(view) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != view.ordinal) {
            pagerState.animateScrollToPage(view.ordinal)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("行程") }, windowInsets = WindowInsets(0, 0, 0, 0))
        TabRow(selectedTabIndex = view.ordinal) {
            TripsView.entries.forEach { entry ->
                Tab(
                    selected = entry == view,
                    onClick = { onViewChange(entry) },
                    text = { Text(entry.label) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            TripsPage(
                view = TripsView.entries[page],
                documents = documents,
                archivedDocuments = archivedDocuments,
                onTripClick = onTripClick,
                onArchive = onArchive,
            )
        }
    }
}

@Composable
private fun TripsPage(
    view: TripsView,
    documents: List<SavedTravelDocument>,
    archivedDocuments: List<SavedTravelDocument>,
    onTripClick: (SavedTravelDocument) -> Unit,
    onArchive: (SavedTravelDocument) -> Unit,
) {
    val visibleDocuments = when (view) {
        TripsView.UPCOMING -> documents
        TripsView.HISTORY -> archivedDocuments
    }
    if (visibleDocuments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (view) {
                        TripsView.UPCOMING -> "还没有未出发的行程"
                        TripsView.HISTORY -> "还没有历史行程"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (view == TripsView.UPCOMING) {
                    Text(
                        "点击右下角添加行程",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visibleDocuments, key = { it.document.stableId() }) { saved ->
                if (view == TripsView.UPCOMING) {
                    UpcomingTripCard(
                        saved = saved,
                        onClick = { onTripClick(saved) },
                        onArchive = { onArchive(saved) },
                    )
                } else {
                    CompactTripCard(saved = saved, onClick = { onTripClick(saved) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpcomingTripCard(
    saved: SavedTravelDocument,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        CompactTripCard(
            saved = saved,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("归档") },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTripCard(
    saved: SavedTravelDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val segment = saved.document.segments.first()
    val seat = segment.seatAssignments.firstOrNull {
        it.status == TravelDocumentStatus.CONFIRMED
    } ?: segment.seatAssignments.firstOrNull()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        segment.serviceNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (saved.document.status != TravelDocumentStatus.CONFIRMED) {
                        Text(
                            saved.document.status.displayName,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Text(
                    segment.departureTime.format(COMPACT_DATE_FORMAT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                segment.railRoute,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    segment.departureTime.format(COMPACT_TIME_FORMAT),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    seat?.let { "${it.section} 车 ${it.seat}" } ?: "座位待确认",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TripDetailDialog(
    saved: SavedTravelDocument,
    walletAvailability: WalletAvailability,
    showGoogleWalletAction: Boolean,
    departureReminderMinutes: Int,
    liveStatusMinutes: Int,
    onDismiss: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onTestReminder: (() -> Unit)?,
    onAddToGoogleWallet: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val document = saved.document
    val segment = document.segments.first()
    val confirmed = document.status == TravelDocumentStatus.CONFIRMED
    val hasDeparted = document.hasDeparted()
    var showDeleteConfirmation by remember(saved.document.stableId()) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    segment.serviceNumber,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    segment.railRoute,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
                segment.arrivalTime?.let { DetailRow("到达", it.format(DEPARTURE_FORMAT)) }
                DetailRow(
                    "座位",
                    segment.seatAssignments.joinToString("、") { assignment ->
                        "${assignment.section} 车 ${assignment.seat}" +
                            if (assignment.status == TravelDocumentStatus.CONFIRMED) {
                                ""
                            } else {
                                "（${assignment.status.displayName}）"
                            }
                    }.ifEmpty { "待确认" },
                )
                val seatCategories = segment.seatAssignments.map { it.category }.distinct()
                if (seatCategories.isNotEmpty()) DetailRow("席别", seatCategories.joinToString("、"))
                DetailRow("乘车人", document.travelers.map { it.name }.distinct().joinToString("、"))
                if (!confirmed) DetailRow("状态", document.status.displayName)
                DetailRow("订单", document.reservation.reference)
                DetailRow("来源", document.provider.name)

                if (!saved.archived && confirmed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("乘车提醒", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasDeparted) {
                                    "行程已结束，无法开启提醒"
                                } else {
                                    "发车前 ${formatLeadTime(departureReminderMinutes)}提醒，" +
                                        "发车前 ${formatLeadTime(liveStatusMinutes)}显示实时状态"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = saved.reminderEnabled && !hasDeparted,
                            onCheckedChange = onReminderChange,
                            enabled = !hasDeparted,
                        )
                    }
                }

                if (onTestReminder != null && !hasDeparted && confirmed) {
                    TextButton(
                        onClick = onTestReminder,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("测试锁屏提醒")
                    }
                }

                if (showGoogleWalletAction && !saved.archived && confirmed) {
                    when (walletAvailability) {
                        WalletAvailability.AVAILABLE -> GoogleWalletButton(
                            onClick = onAddToGoogleWallet,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp),
                        )

                        WalletAvailability.UNAVAILABLE -> Text(
                            "请安装或更新 Google Wallet 后重试。",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )

                        WalletAvailability.CHECKING -> Unit
                    }
                }

                if (saved.archived && confirmed) {
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    ) {
                        Text("恢复到我的行程")
                    }
                } else if (!saved.archived) {
                    OutlinedButton(
                        onClick = onArchive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    ) {
                        Text("归档行程")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("删除行程", modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除这趟行程？") },
            text = { Text("删除后，如需再次查看，需要重新导入行程。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTripSheet(
    onDismiss: () -> Unit,
    onEmailImport: () -> Unit,
    onTextImport: () -> Unit,
    onScreenshotImport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "添加行程",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        ListItem(
            headlineContent = { Text("从邮箱同步") },
            supportingContent = { Text("通过已配置的邮箱读取铁路订单通知") },
            leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onEmailImport),
        )
        ListItem(
            headlineContent = { Text("粘贴邮件正文") },
            supportingContent = { Text("手动粘贴 12306 铁路订单通知") },
            leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onTextImport),
        )
        ListItem(
            headlineContent = { Text("从截图识别") },
            supportingContent = { Text("选择图片并在设备上读取文字") },
            leadingContent = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onScreenshotImport),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    newTripsReminderEnabled: Boolean,
    autoArchiveDepartedTrips: Boolean,
    departureReminderMinutes: Int,
    liveStatusMinutes: Int,
    googleWalletActionVisible: Boolean,
    themeMode: ThemeMode,
    emailAccountSummary: ImapAccountSummary?,
    automaticEmailSyncEnabled: Boolean,
    automaticEmailSyncInterval: AutomaticEmailSyncInterval,
    automaticEmailSyncStatus: AutomaticEmailSyncStatus,
    automaticEmailSyncStatusAt: Long,
    onEmailAccountClick: () -> Unit,
    onAutomaticEmailSyncChange: (Boolean) -> Unit,
    onAutomaticEmailSyncIntervalChange: (AutomaticEmailSyncInterval) -> Unit,
    onNewTripsReminderChange: (Boolean) -> Unit,
    onAutoArchiveDepartedTripsChange: (Boolean) -> Unit,
    onDepartureReminderMinutesChange: (Int) -> Unit,
    onLiveStatusMinutesChange: (Int) -> Unit,
    onGoogleWalletVisibilityChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var syncIntervalMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") }, windowInsets = WindowInsets(0, 0, 0, 0))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    "导入",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("邮箱同步") },
                    supportingContent = {
                        Text(
                            emailAccountSummary?.let {
                                val folder = it.folderName?.let { name -> " · $name" }.orEmpty()
                                "已配置 ${it.emailAddress}$folder"
                            } ?: "未配置，支持使用 IMAP 专用密码或授权码的邮箱",
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onEmailAccountClick),
                )
            }
            item {
                SettingSwitch(
                    title = "自动同步邮箱",
                    description = when (automaticEmailSyncStatus) {
                        AutomaticEmailSyncStatus.FAILED -> "上次同步失败，请检查邮箱配置"
                        AutomaticEmailSyncStatus.INITIAL_SYNC_REQUIRED ->
                            "请先完成一次手动邮箱同步"
                        AutomaticEmailSyncStatus.SUCCESS -> if (automaticEmailSyncStatusAt > 0) {
                            "上次同步：${formatSyncTime(automaticEmailSyncStatusAt)}"
                        } else {
                            "发现新行程时自动保存并通知"
                        }
                        AutomaticEmailSyncStatus.NEVER -> "发现新行程时自动保存并通知"
                    },
                    checked = automaticEmailSyncEnabled,
                    onCheckedChange = onAutomaticEmailSyncChange,
                    enabled = emailAccountSummary != null,
                )
            }
            item {
                AnimatedVisibility(
                    visible = automaticEmailSyncEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Box {
                        ListItem(
                            headlineContent = { Text("同步间隔") },
                            supportingContent = {
                                Text(automaticEmailSyncInterval.displayName())
                            },
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable {
                                    syncIntervalMenuExpanded = true
                                },
                        )
                        DropdownMenu(
                            expanded = syncIntervalMenuExpanded,
                            onDismissRequest = { syncIntervalMenuExpanded = false },
                        ) {
                            AutomaticEmailSyncInterval.entries.forEach { interval ->
                                DropdownMenuItem(
                                    text = { Text(interval.displayName()) },
                                    onClick = {
                                        syncIntervalMenuExpanded = false
                                        onAutomaticEmailSyncIntervalChange(interval)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "行程管理",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingSwitch(
                    title = "自动归档已结束行程",
                    description = "只在未来行程出发时归档，不追溯已结束行程",
                    checked = autoArchiveDepartedTrips,
                    onCheckedChange = onAutoArchiveDepartedTripsChange,
                )
            }
            item {
                Text(
                    "乘车提醒",
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingSwitch(
                    title = "新行程默认提醒",
                    description = "导入新行程时自动开启乘车提醒",
                    checked = newTripsReminderEnabled,
                    onCheckedChange = onNewTripsReminderChange,
                )
            }
            item {
                ReminderTimingSlider(
                    title = "发车前提醒",
                    minutes = departureReminderMinutes,
                    minimum = ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
                    maximum = ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
                    stepMinutes = ReminderTimingConstraints.DEPARTURE_STEP_MINUTES,
                    onMinutesChange = onDepartureReminderMinutesChange,
                )
            }
            item {
                ReminderTimingSlider(
                    title = "显示实时状态",
                    minutes = liveStatusMinutes,
                    minimum = ReminderTimingConstraints.LIVE_MIN_MINUTES,
                    maximum = ReminderTimingConstraints.LIVE_MAX_MINUTES,
                    stepMinutes = ReminderTimingConstraints.LIVE_STEP_MINUTES,
                    onMinutesChange = onLiveStatusMinutesChange,
                )
            }
            item {
                SettingSwitch(
                    title = "显示 Google Wallet",
                    description = "在行程详情中显示添加至 Google Wallet 的入口",
                    checked = googleWalletActionVisible,
                    onCheckedChange = onGoogleWalletVisibilityChange,
                )
            }
            item {
                Text(
                    "外观",
                    modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(ThemeMode.entries) { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeModeChange(mode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    Text(label, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderTimingSlider(
    title: String,
    minutes: Int,
    minimum: Int,
    maximum: Int,
    stepMinutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    var pendingMinutes by remember(minutes) { mutableStateOf(minutes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                formatLeadTime(pendingMinutes),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = pendingMinutes.toFloat(),
            onValueChange = { value ->
                pendingMinutes = (value / stepMinutes).roundToInt() * stepMinutes
            },
            onValueChangeFinished = { onMinutesChange(pendingMinutes) },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = (maximum - minimum) / stepMinutes - 1,
        )
        Text(
            "可设置为 ${formatLeadTime(minimum)}至 ${formatLeadTime(maximum)}，" +
                "每次调整 ${formatLeadTime(stepMinutes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailAccountScreen(
    existingAccount: ImapAccountSummary?,
    testState: EmailAccountTestState,
    folderState: EmailFolderState,
    onBack: () -> Unit,
    onSave: (ImapAccountConfig) -> Unit,
    onLoadFolders: () -> Unit,
    onFolderSelected: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    var preset by rememberSaveable {
        mutableStateOf(
            ImapProviderPreset.entries.firstOrNull { it.host == existingAccount?.host }
                ?: ImapProviderPreset.CUSTOM,
        )
    }
    var emailAddress by rememberSaveable { mutableStateOf(existingAccount?.emailAddress.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existingAccount?.emailAddress.orEmpty()) }
    var usernameEdited by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf(existingAccount?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf("993") }
    var credential by rememberSaveable { mutableStateOf("") }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var folderRequestInProgress by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val portNumber = port.toIntOrNull()
    LaunchedEffect(folderState) {
        when (folderState) {
            EmailFolderState.Loading -> folderRequestInProgress = true
            is EmailFolderState.Success -> if (folderRequestInProgress) {
                folderRequestInProgress = false
                folderMenuExpanded = true
            }
            is EmailFolderState.Error -> folderRequestInProgress = false
            EmailFolderState.Idle -> Unit
        }
    }
    val canSave = emailAddress.isNotBlank() && username.isNotBlank() && host.isNotBlank() &&
        portNumber in 1..65535 && credential.isNotBlank() &&
        testState != EmailAccountTestState.Testing

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("邮箱配置", onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                "请使用邮箱提供的专用密码或客户端授权码，不要填写邮箱登录密码。" +
                    "当前只支持使用 TLS 的 IMAP 服务器。",
            )
            Text(
                "邮箱服务商",
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            ImapProviderPreset.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            preset = option
                            option.host?.let { host = it }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = preset == option,
                        onClick = {
                            preset = option
                            option.host?.let { host = it }
                        },
                    )
                    Text(option.displayName)
                }
            }
            Text(
                when (preset) {
                    ImapProviderPreset.GMAIL -> "Gmail 需要先开启两步验证，并在 Google 账号中创建应用专用密码。"
                    ImapProviderPreset.QQ_MAIL -> "请先在 QQ 邮箱设置中开启 IMAP 服务并生成授权码。"
                    ImapProviderPreset.NETEASE_163,
                    ImapProviderPreset.NETEASE_126,
                    -> "请先在网易邮箱设置中开启 IMAP 服务并生成客户端授权码。"
                    ImapProviderPreset.CUSTOM -> "请向邮箱服务商确认 IMAP 服务器、端口和专用凭据。"
                },
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preset == ImapProviderPreset.GMAIL) {
                TextButton(
                    onClick = { uriHandler.openUri("https://myaccount.google.com/apppasswords") },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("打开应用专用密码设置")
                }
            }
            OutlinedTextField(
                value = emailAddress,
                onValueChange = {
                    emailAddress = it.trim()
                    if (!usernameEdited) username = emailAddress
                },
                label = { Text("邮箱地址") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.trim()
                    usernameEdited = true
                },
                label = { Text("IMAP 用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("IMAP 服务器") },
                singleLine = true,
                enabled = preset == ImapProviderPreset.CUSTOM,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = credential,
                onValueChange = { credential = it },
                label = { Text("专用密码或授权码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            when (testState) {
                EmailAccountTestState.Idle -> Unit
                EmailAccountTestState.Testing -> Text(
                    "正在测试连接…",
                    modifier = Modifier.padding(top = 12.dp),
                )
                EmailAccountTestState.Success -> Text(
                    "连接成功，邮箱配置已保存。",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                is EmailAccountTestState.Error -> Text(
                    testState.message,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    onSave(
                        ImapAccountConfig(
                            emailAddress = emailAddress,
                            username = username,
                            host = host,
                            port = requireNotNull(portNumber),
                            credential = if (preset == ImapProviderPreset.CUSTOM) {
                                credential
                            } else {
                                credential.filterNot(Char::isWhitespace)
                            },
                            folderName = existingAccount?.folderName,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text(if (testState == EmailAccountTestState.Testing) "正在测试…" else "测试并保存")
            }
            if (existingAccount != null) {
                Text(
                    "同步文件夹",
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "默认自动选择全部邮件或收件箱。指定文件夹后，只会检查该文件夹。" +
                        "请自行在邮箱服务商处配置收信规则或过滤器，确保新的铁路通知会进入这里。",
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onLoadFolders,
                        enabled = folderState != EmailFolderState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                folderState == EmailFolderState.Loading -> "正在获取邮箱文件夹…"
                                existingAccount.folderName != null -> existingAccount.folderName
                                folderState is EmailFolderState.Error -> "重新获取邮箱文件夹"
                                folderState is EmailFolderState.Success -> "选择邮箱文件夹"
                                else -> "点击获取邮箱文件夹"
                            },
                        )
                    }
                    val folders = (folderState as? EmailFolderState.Success)?.folders.orEmpty()
                    DropdownMenu(
                        expanded = folderMenuExpanded && folders.isNotEmpty(),
                        onDismissRequest = { folderMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("自动选择") },
                            onClick = {
                                folderMenuExpanded = false
                                onFolderSelected(null)
                            },
                        )
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.fullName) },
                                onClick = {
                                    folderMenuExpanded = false
                                    onFolderSelected(folder.fullName)
                                },
                            )
                        }
                    }
                }
                if (folderState is EmailFolderState.Error) {
                    Text(
                        folderState.message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                ) {
                    Text("删除邮箱配置", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除邮箱配置？") },
            text = { Text("邮箱地址、授权码和同步记录将从此设备移除。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmailSyncProgressContent(
    progress: EmailSyncProgress,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val text = when (progress) {
        EmailSyncProgress.Connecting -> "正在连接邮箱"
        EmailSyncProgress.CheckingMessages -> "正在检查新邮件"
        is EmailSyncProgress.ReadingRailwayMessages -> {
            val current = (progress.completed + 1).coerceAtMost(progress.total)
            "正在读取铁路订单（$current/${progress.total}）"
        }
        EmailSyncProgress.OrganizingTrips -> "正在整理行程"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(text, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailImportScreen(
    state: EmailImportState,
    onDone: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("从邮箱同步", onDone) },
    ) { contentPadding ->
        when (state) {
            EmailImportState.Idle -> EmailSyncProgressContent(
                progress = EmailSyncProgress.Connecting,
                contentPadding = contentPadding,
            )

            is EmailImportState.Loading -> EmailSyncProgressContent(
                progress = state.progress,
                contentPadding = contentPadding,
            )

            is EmailImportState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("无法导入邮件", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.message,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    OutlinedButton(onClick = onDone) { Text("返回") }
                }
            }

            is EmailImportState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.documents.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                state.warnings.joinToString("\n").ifEmpty {
                                    "没有找到可导入的铁路行程。已结束行程可能被当前设置过滤。"
                                },
                            )
                            OutlinedButton(
                                onClick = onDone,
                                modifier = Modifier.padding(top = 20.dp),
                            ) {
                                Text("完成")
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "已按订单更新本地记录，以下是本次同步的行程。",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                    if (state.warnings.isNotEmpty()) {
                        item {
                            Text(
                                state.warnings.joinToString("\n"),
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(state.documents, key = { it.stableId() }) { document ->
                        CompactTripCard(
                            saved = SavedTravelDocument(
                                document = document,
                                reminderEnabled = false,
                                archived = document.status != TravelDocumentStatus.CONFIRMED ||
                                    document.hasDeparted(),
                            ),
                            onClick = {},
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item {
                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            Text("完成")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotImportScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("截图识别", onBack) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "正在读取截图…",
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScreen(
    importMode: ImportMode,
    emailBody: String,
    error: String?,
    onBodyChange: (String) -> Unit,
    onUseSample: (() -> Unit)?,
    onBack: () -> Unit,
    onParse: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            PageTopBar(
                if (importMode == ImportMode.SCREENSHOT) "检查识别文字" else "粘贴邮件正文",
                onBack,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                if (importMode == ImportMode.SCREENSHOT) {
                    "请检查截图中读取出的文字，修正错误后再识别行程。"
                } else {
                    "粘贴 12306 购票、改签、候补或退票邮件正文，解析后请核对行程状态。"
                },
            )
            OutlinedTextField(
                value = emailBody,
                onValueChange = onBodyChange,
                label = {
                    Text(if (importMode == ImportMode.SCREENSHOT) "识别文字" else "邮件正文")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .padding(top = 20.dp),
            )
            if (onUseSample != null) {
                TextButton(
                    onClick = onUseSample,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("填入提醒测试数据")
                }
            }
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onParse,
                enabled = emailBody.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(if (importMode == ImportMode.SCREENSHOT) "识别行程" else "解析邮件")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationScreen(
    documents: List<TravelDocument>,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { PageTopBar("确认行程", onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("请确认行程和状态是否识别正确。保存后会按订单更新本地记录。")
            documents.forEach { document ->
                TripInformation(
                    document = document,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(if (documents.size == 1) "保存行程" else "保存 ${documents.size} 个行程")
            }
        }
    }
}

@Composable
private fun TripInformation(document: TravelDocument, modifier: Modifier = Modifier) {
    val segment = document.segments.first()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(segment.serviceNumber, style = MaterialTheme.typography.headlineSmall)
            Text(
                segment.railRoute,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
            DetailRow(
                "座位",
                segment.seatAssignments.joinToString("、") { assignment ->
                    "${assignment.section} 车 ${assignment.seat}" +
                        if (assignment.status == TravelDocumentStatus.CONFIRMED) {
                            ""
                        } else {
                            "（${assignment.status.displayName}）"
                        }
                }.ifEmpty { "待确认" },
            )
            val categories = segment.seatAssignments.map { it.category }.distinct()
            if (categories.isNotEmpty()) DetailRow("席别", categories.joinToString("、"))
            DetailRow("乘车人", document.travelers.map { it.name }.distinct().joinToString("、"))
            DetailRow("状态", document.status.displayName)
            DetailRow("订单", document.reservation.reference)
        }
    }
}

@Composable
private fun GoogleWalletButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .padding(8.dp)
            .widthIn(min = 270.dp)
            .height(49.dp)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color(0xFF5F6368), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.add_to_google_wallet_button_foreground),
            contentDescription = "添加至 Google Wallet",
            modifier = Modifier
                .width(227.dp)
                .height(26.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun screenshotParseError(message: String): String = when {
    message.startsWith("这封邮件") -> "截图文字中没有找到完整的铁路购票信息，请检查识别文字。"
    else -> message
        .replace("邮件中", "截图文字中")
        .replace("邮件内容", "识别文字")
        .replace("邮件显示", "截图文字显示")
}

private val TravelDocumentStatus.displayName: String
    get() = when (this) {
        TravelDocumentStatus.CONFIRMED -> "有效"
        TravelDocumentStatus.RESCHEDULED -> "已改签"
        TravelDocumentStatus.REFUNDED -> "已退票"
    }

private fun formatLeadTime(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        remainingMinutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分钟"
    }
}

/**
 * 每分钟更新一次的时刻。发车倒计时和「是否已经出发」共用同一个时间源，
 * 对齐到整分钟，避免每秒触发重组。
 */
@Composable
private fun rememberMinuteTicker(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MILLIS - System.currentTimeMillis() % MINUTE_MILLIS)
            now = Instant.now()
        }
    }
    return now
}

private fun formatDepartureCountdown(departure: ZonedDateTime, now: Instant): String {
    val minutes = Duration.between(now, departure.toInstant()).toMinutes()
    if (minutes <= 0) return "即将发车"
    val days = minutes / (24 * 60)
    val hours = minutes % (24 * 60) / 60
    val remainingMinutes = minutes % 60
    return when {
        days > 0 && hours > 0 -> "距发车 $days 天 $hours 小时"
        days > 0 -> "距发车 $days 天"
        hours > 0 && remainingMinutes > 0 -> "距发车 $hours 小时 $remainingMinutes 分钟"
        hours > 0 -> "距发车 $hours 小时"
        else -> "距发车 $remainingMinutes 分钟"
    }
}

private const val MINUTE_MILLIS = 60_000L

private val DEPARTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm")
private val COMPACT_DATE_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日")
private val COMPACT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val SAMPLE_EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private val SAMPLE_EMAIL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH:mm")
private val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TEST_STATION_NAMES = listOf(
    "苹果站",
    "香蕉站",
    "橙子站",
    "葡萄站",
    "芒果站",
    "桃子站",
    "草莓站",
    "西瓜站",
)
private val TEST_SEAT_LETTERS = charArrayOf('A', 'B', 'C', 'D', 'F')

private fun createUpcomingReminderSample(): String {
    val now = ZonedDateTime.now(SHANGHAI_ZONE)
    val departure = now
        .plusMinutes(Random.nextLong(24 * 60L, 48 * 60L + 1))
        .withSecond(0)
        .withNano(0)
    val origin = TEST_STATION_NAMES.random()
    val destination = TEST_STATION_NAMES.filterNot { it == origin }.random()
    val serviceNumber = "G${Random.nextInt(1_000, 10_000)}"
    val carriage = Random.nextInt(1, 13)
    val seat = "${Random.nextInt(1, 25)}${TEST_SEAT_LETTERS.random()}"
    val reservationReference = "E${Random.nextLong(100_000_000L, 1_000_000_000L)}"
    return """
        尊敬的 测试乘客先生：
        您好！
        您于${now.format(SAMPLE_EMAIL_DATE_FORMAT)}在中国铁路客户服务中心网站(12306.cn)成功购买了1张车票，票款共计120.00元，订单号码 $reservationReference。所购车票信息如下：
        1.测试乘客，${departure.format(SAMPLE_EMAIL_DATE_TIME_FORMAT)}开，$origin-$destination，${serviceNumber}次列车，${carriage}车${seat}号，二等座，成人票，票价120.0元，电子客票。
    """.trimIndent()
}
