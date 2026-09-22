package com.autoseer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.autoseer.R
import android.graphics.Bitmap
import com.autoseer.capture.ScreenCaptureManager
import com.autoseer.core.AndroidLogger
import com.autoseer.core.DelayPrefs
import com.autoseer.core.DeviceTemplates
import com.autoseer.core.OpenCvMatcher
import com.autoseer.core.SeerStorage
import com.autoseer.input.GestureAccessibilityService
import com.autoseer.libautomata.IGestureService
import com.autoseer.libautomata.Location
import com.autoseer.overlay.ControlOverlay
import com.autoseer.core.ScriptStore
import com.autoseer.core.SeerScript
import com.autoseer.runner.ScriptRunner
import com.autoseer.scripts.BattleScript
import com.autoseer.scripts.BattlePlanParser
import com.autoseer.scripts.ProbeScript
import com.autoseer.scripts.SeerFactorScript
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that owns the whole automation runtime: MediaProjection
 * capture, the floating control overlay, and the [ScriptRunner]. Started by
 * [com.autoseer.ui.MainActivity] once the user grants screen capture.
 */
class AutoSeerService : Service() {

    private var capture: ScreenCaptureManager? = null
    private var overlay: ControlOverlay? = null
    private var runner: ScriptRunner? = null

    // Resolves the accessibility service lazily so it works once it connects.
    private val gestures = object : IGestureService {
        override fun click(location: Location, durationMs: Long) {
            val svc = GestureAccessibilityService.instance
            if (svc == null) Log.w(TAG, "無障礙服務未連線，無法點擊") else svc.click(location, durationMs)
        }
        override fun swipe(from: Location, to: Location, durationMs: Long) {
            GestureAccessibilityService.instance?.swipe(from, to, durationMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        startForegroundInternal()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            Log.e(TAG, "缺少螢幕擷取授權資料，停止服務")
            stopEverything()
            return START_NOT_STICKY
        }

        setup(resultCode, data)
        return START_NOT_STICKY
    }

    private fun setup(resultCode: Int, data: Intent) {
        // Re-authorizing while already running must not stack overlays/captures:
        // release any previous runtime first (BUG-1), then build fresh from the
        // new projection. This does not stop the service (no stopForeground/Self).
        releaseRuntime()

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection = mpm.getMediaProjection(resultCode, data)

        val metrics = resources.displayMetrics
        val capture = ScreenCaptureManager(
            projection = projection,
            deviceWidth = metrics.widthPixels,
            deviceHeight = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
        ).also { it.start() }
        this.capture = capture

        val overlay = ControlOverlay(
            context = this,
            onStart = { startScript() },
            onStop = { runner?.stop() },
            onCapture = { captureFrame() },
            onProbe = { probeDetection() },
        )
        this.overlay = overlay
        val logger = AndroidLogger(onLine = { line -> overlay.setStatus(line) })

        runner = ScriptRunner(
            screenshotProvider = capture,
            matcher = OpenCvMatcher(),
            gestures = gestures,
            logger = logger,
            onStateChange = { running -> overlay.setRunning(running) },
        )
        overlay.show()
        Log.i(TAG, "AutoSeerService 就緒：device=${metrics.widthPixels}x${metrics.heightPixels}")
    }

    private fun startScript() {
        val runner = runner ?: return
        if (!GestureAccessibilityService.isConnected) {
            overlay?.setStatus("⚠ 無障礙服務未連線，無法點擊。請到設定開啟後再試")
            Log.w(TAG, "無障礙服務未連線，取消啟動腳本")
            return
        }
        val templates = DeviceTemplates(this)
        ScriptStore.ensureSeeded(this)
        val script = ScriptStore.selected(this)
        if (script == null) {
            overlay?.setStatus("⚠ 尚未設定任何腳本，請先到「周回腳本」新增")
            Log.w(TAG, "無選取腳本，取消啟動")
            return
        }
        val parsed = BattlePlanParser.parse(
            text = script.planText,
            maxBattles = script.maxBattles,
            healBeforeBattle = script.healBeforeBattle,
            advanceMap = script.advanceMap,
            defaultSlot = script.defaultSlot,
            startStage = script.startStage,
            maxRetriesPerStage = script.maxRetries,
            loops = script.loops,
        )
        parsed.warnings.forEach { Log.w(TAG, "計畫解析警告：$it") }
        val delays = DelayPrefs.toBattleDelays(this)
        overlay?.setStatus("腳本「${script.displayName}」 ${BattlePlanParser.describe(parsed.plan)}")
        runner.start { api ->
            if (script.category == SeerScript.CATEGORY_SEER_FACTOR) {
                SeerFactorScript(api, templates, parsed.plan, delays)
            } else {
                BattleScript(api, templates, parsed.plan, delays)
            }
        }
    }

    /** Save the current normalized color frame to <externalFilesDir>/captures for cropping into templates. */
    private fun captureFrame() {
        val cap = capture ?: run { overlay?.setStatus("尚未就緒，無法擷取"); return }
        Thread {
            try {
                val bmp = cap.captureColorBitmap()
                val dir = SeerStorage.capturesDir(this)
                val file = File(dir, "cap_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                Log.i(TAG, "已存畫面：${file.absolutePath}")
                overlay?.setStatus("已存畫面：${file.name}\n${dir.absolutePath}")
            } catch (e: Throwable) {
                Log.e(TAG, "存畫面失敗", e)
                overlay?.setStatus("存畫面失敗：${e.message}")
            }
        }.start()
    }

    /** Run the detection probe: report each template's match score without tapping anything. */
    private fun probeDetection() {
        val runner = runner ?: return
        overlay?.setStatus("偵測測試中…（結果見狀態列/Logcat）")
        runner.start { api -> ProbeScript(api, DeviceTemplates(this)) }
    }

    private fun startForegroundInternal() {
        val channelId = "autoseer_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /** Tear down the capture/overlay/runner without stopping the service itself. */
    private fun releaseRuntime() {
        runner?.stop()
        overlay?.hide()
        capture?.release()
        runner = null
        overlay = null
        capture = null
    }

    private fun stopEverything() {
        releaseRuntime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoSeer"
        private const val NOTIF_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val ACTION_STOP = "com.autoseer.action.STOP"

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, AutoSeerService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, AutoSeerService::class.java).apply { action = ACTION_STOP }
    }
}
