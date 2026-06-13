package net.ogatomo.karaplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlin.concurrent.thread

class DownloadService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ensureKaraPlayFolder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> startDownload(intent.getStringExtra(EXTRA_URL).orEmpty())
            ACTION_UPDATE_YTDLP -> updateYtDlp()
            ACTION_REFRESH_VERSION -> refreshVersion()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDownload(url: String) {
        if (DownloadStore.isRunning.value) {
            postLog("すでにダウンロード中です")
            return
        }
        if (!isAllowedYoutubeUrl(url)) {
            postLog("youtube.com または youtu.be の URL だけ利用できます")
            return
        }
        val notification = buildNotification("ダウンロード準備中", 0)

        // 👇 ここを try-catch で安全化します！
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            postLog("フォアグラウンドの起動がOSに制限されました。バックグラウンドで処理を継続します。")
        }

        thread(name = "KaraPlayDownload") {
            postState(running = true, progress = 0f, status = "ダウンロード中")
            val dir = ensureKaraPlayFolder()
            if (!dir.exists()) {
                postState(false, 0f, "保存先を作成できません: ${dir.absolutePath}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                return@thread
            }
            var downloadSuccess = false
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("-f", "140")
                request.addOption("--no-playlist")
                request.addOption("--embed-metadata")
                // 👇 ここが主役！これを付けるだけでFFmpegが自動でm4aにサムネを埋め込みます
                request.addOption("--embed-thumbnail")
                // 👇 m4aに埋め込むためにサムネイルをjpg形式に指定します
                request.addOption("--convert-thumbnails", "jpg")
                request.addOption("-o", "${dir.absolutePath}/%(title)s - %(uploader)s.%(ext)s")

                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    val cleanLine = line.takeIf { it.isNotBlank() } ?: "進捗 ${progress.toInt()}% / 残り ${etaInSeconds}秒"
                    postState(true, progress, cleanLine)
                    notifyProgress(cleanLine, progress.toInt())
                }

                // 埋め込みはyt-dlpが完了させてくれるので、手動のクロップや結合コードは全削除でOK！
                postState(false, 100f, "ダウンロードが完了しました")
                downloadSuccess = true
            } catch (exception: Exception) {
                postState(false, 0f, "ダウンロードに失敗しました: ${exception.message ?: "詳細不明"}")
                notifyProgress("ダウンロードに失敗しました", 0)
                downloadSuccess = false
            } finally {
                if (downloadSuccess) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                stopSelf()
            }
        }
    }

    private fun updateYtDlp() {
        postState(true, 0f, "yt-dlp を更新中")
        thread(name = "KaraPlayYtDlpUpdate") {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
                postState(false, 100f, "yt-dlp を更新しました")
            } catch (exception: YoutubeDLException) {
                postState(false, 0f, "yt-dlp の更新に失敗しました: ${exception.message ?: "詳細不明"}")
            } finally {
                refreshVersion()
                stopSelf()
            }
        }
    }

    private fun refreshVersion() {
        thread(name = "KaraPlayYtDlpVersion") {
            val version = runCatching { YoutubeDL.getInstance().versionName(this) ?: "不明" }
                .getOrElse { "取得失敗" }
            mainHandler.post {
                DownloadStore.ytdlpVersion.value = version
                DownloadStore.log("yt-dlp バージョン: $version")
                stopSelf()
            }
        }
    }

    private fun postState(running: Boolean, progress: Float, status: String) {
        mainHandler.post {
            DownloadStore.isRunning.value = running
            DownloadStore.progress.floatValue = progress
            DownloadStore.status.value = status
            DownloadStore.log(status)
        }
    }

    private fun postLog(message: String) {
        mainHandler.post {
            DownloadStore.status.value = message
            DownloadStore.log(message)
        }
    }

    private fun notifyProgress(text: String, progress: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingFlags()
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("KaraPlay ダウンロード")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(DownloadStore.isRunning.value)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ダウンロード", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun pendingFlags() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    companion object {
        const val ACTION_DOWNLOAD = "net.ogatomo.karaplay.DOWNLOAD"
        const val ACTION_UPDATE_YTDLP = "net.ogatomo.karaplay.UPDATE_YTDLP"
        const val ACTION_REFRESH_VERSION = "net.ogatomo.karaplay.REFRESH_VERSION"
        const val EXTRA_URL = "url"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2001
    }
}

fun Context.startDownload(url: String) {
    // 💡 Android 15以降のバックグラウンド起動による強制終了（Kill）を防ぐため、
    // startForegroundService ではなく通常の startService で安全に立ち上げます。
    val intent = Intent(this, DownloadService::class.java)
        .setAction(DownloadService.ACTION_DOWNLOAD)
        .putExtra(DownloadService.EXTRA_URL, url)
    startService(intent)
}

fun Context.updateYtDlp() {
    startService(Intent(this, DownloadService::class.java).setAction(DownloadService.ACTION_UPDATE_YTDLP))
}

fun Context.refreshYtDlpVersion() {
    startService(Intent(this, DownloadService::class.java).setAction(DownloadService.ACTION_REFRESH_VERSION))
}