package com.example.nojump

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BlockService : Service() {

    private val thread = Thread({ runLoop() }, "NoJumpMonitor").apply { isDaemon = true }
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RuleStore.init(this)
        ForegroundWatcher.init(this)
        RuleEngine.configure(packageName)
        // 清理上次异常退出可能残留的冻结（杀后台时 onDestroy 不一定执行）
        Thread { Freezer.unfreezeAll() }.start()
        createChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        running = true
        thread.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                RuleStore.paused = true
                Freezer.unfreezeAll()
                notifyUpdate()
            }
            ACTION_RESUME -> {
                RuleStore.paused = false
                notifyUpdate()
            }
            ACTION_STOP_SHIZUKU -> openShizukuApp()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        thread.interrupt()
        runCatching { thread.join(1000) }
        Freezer.unfreezeAll()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户在“最近任务”里滑掉本应用时，恢复被隐藏的目标应用
        Freezer.unfreezeAll()
    }

    private fun runLoop() {
        ForegroundWatcher.pollForeground()
        while (running) {
            if (RuleStore.paused) {
                sleep(400)
                continue
            }
            RuleEngine.onTick(ForegroundWatcher.pollForeground(), System.currentTimeMillis())
            notifyUpdate()
            sleep(RuleStore.pollIntervalMs.coerceIn(300, 5000))
        }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun notifyUpdate() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val paused = RuleStore.paused
        val frozenCount = Freezer.frozenPackages().size

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BlockService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val shizukuIntent = PendingIntent.getService(
            this, 2,
            Intent(this, BlockService::class.java).setAction(ACTION_STOP_SHIZUKU),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(if (paused) "NoJump 已暂停" else "NoJump 监控中")
            .setContentText(if (paused) "点「继续」恢复拦截" else "已冻结应用：$frozenCount 个")
            .addAction(0, if (paused) "继续" else "暂停", pauseIntent)
            .addAction(0, "去停 Shizuku", shizukuIntent)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "NoJump 拦截监控", NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val CHANNEL_ID = "nojump"
        private const val NOTIF_ID = 1
        const val ACTION_PAUSE = "com.example.nojump.PAUSE"
        const val ACTION_RESUME = "com.example.nojump.RESUME"
        const val ACTION_STOP_SHIZUKU = "com.example.nojump.STOP_SHIZUKU"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BlockService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockService::class.java))
        }
    }
}