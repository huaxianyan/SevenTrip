package com.neko7ina.wallet.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.neko7ina.wallet.assistant.email.EmailSyncNotification
import com.neko7ina.wallet.assistant.screenshot.ScreenshotRecognitionResult
import com.neko7ina.wallet.assistant.screenshot.ScreenshotTextRecognizer
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient

class MainActivity : ComponentActivity() {
    private val walletClient by lazy { Pay.getClient(this) }
    private val alarmManager by lazy { getSystemService(AlarmManager::class.java) }
    private val screenshotTextRecognizer = ScreenshotTextRecognizer()
    private var screenshotRecognitionCallback: ((ScreenshotRecognitionResult) -> Unit)? = null
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    private var exactReminderPermissionCallback: ((Boolean) -> Unit)? = null
    private val openUpcomingTrips = mutableStateOf(false)

    private val exactReminderPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        exactReminderPermissionCallback?.invoke(
            Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms(),
        )
        exactReminderPermissionCallback = null
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionCallback?.invoke(granted)
        notificationPermissionCallback = null
    }
    private val screenshotPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            deliverScreenshotRecognition(ScreenshotRecognitionResult.Cancelled)
        } else {
            screenshotRecognitionCallback?.invoke(ScreenshotRecognitionResult.Processing)
            screenshotTextRecognizer.recognize(
                context = this,
                uri = uri,
                onResult = ::deliverScreenshotRecognition,
            )
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpcomingTrips.value = intent.getBooleanExtra(
            EmailSyncNotification.EXTRA_OPEN_UPCOMING_TRIPS,
            false,
        )
        enableEdgeToEdge()
        setContent {
            TravelWalletApp(
                checkGoogleWalletAvailability = ::checkGoogleWalletAvailability,
                addToGoogleWallet = ::addToGoogleWallet,
                requestScreenshotRecognition = ::requestScreenshotRecognition,
                requestNotificationPermission = ::requestNotificationPermission,
                requestExactReminderPermission = ::requestExactReminderPermission,
                openUpcomingTrips = openUpcomingTrips.value,
                onUpcomingTripsOpened = { openUpcomingTrips.value = false },
                setDarkSystemBars = ::setDarkSystemBars,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EmailSyncNotification.EXTRA_OPEN_UPCOMING_TRIPS, false)) {
            openUpcomingTrips.value = true
        }
    }

    override fun onDestroy() {
        screenshotTextRecognizer.close()
        super.onDestroy()
    }

    @Deprecated("Google Wallet SDK reports save results through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ADD_TO_GOOGLE_WALLET_REQUEST_CODE) return

        when (resultCode) {
            Activity.RESULT_OK -> Toast.makeText(
                this,
                "已添加至 Google Wallet",
                Toast.LENGTH_SHORT,
            ).show()

            Activity.RESULT_CANCELED -> Unit
            PayClient.SavePassesResult.SAVE_ERROR -> {
                Log.e(
                    "GoogleWallet",
                    data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE) ?: "Unknown save error",
                )
                showWalletError()
            }

            else -> showWalletError()
        }
    }

    private fun setDarkSystemBars(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun checkGoogleWalletAvailability(callback: (Boolean) -> Unit) {
        walletClient.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener { status ->
                callback(status == PayApiAvailabilityStatus.AVAILABLE)
            }
            .addOnFailureListener { callback(false) }
    }

    private fun addToGoogleWallet(unsignedPass: String) {
        walletClient.savePasses(unsignedPass, this, ADD_TO_GOOGLE_WALLET_REQUEST_CODE)
    }

    private fun requestScreenshotRecognition(
        callback: (ScreenshotRecognitionResult) -> Unit,
    ) {
        screenshotRecognitionCallback = callback
        screenshotPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun deliverScreenshotRecognition(result: ScreenshotRecognitionResult) {
        screenshotRecognitionCallback?.invoke(result)
        screenshotRecognitionCallback = null
    }

    private fun requestExactReminderPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            callback(true)
            return
        }
        exactReminderPermissionCallback = callback
        exactReminderPermissionLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
            return
        }
        notificationPermissionCallback = callback
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showWalletError() {
        Toast.makeText(
            this,
            "暂时无法添加，请稍后重试。",
            Toast.LENGTH_LONG,
        ).show()
    }

    private companion object {
        const val ADD_TO_GOOGLE_WALLET_REQUEST_CODE = 1000
    }
}
