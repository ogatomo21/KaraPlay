package net.ogatomo.karaplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class PlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) PlaybackStore.positionMs.intValue = it.currentPosition
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "KaraPlay").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seek(pos.toInt())
            })
            isActive = true
        }
        handler.post(progressTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_TRACK -> playTrack(intent.toTrack())
            ACTION_TOGGLE -> if (PlaybackStore.isPlaying.value) pause() else resume()
            ACTION_PAUSE -> pause()
            ACTION_PREVIOUS -> previous()
            ACTION_NEXT -> next()
            ACTION_SEEK -> seek(intent.getIntExtra(EXTRA_POSITION, 0))
            ACTION_SET_KEY -> {
                PlaybackStore.key.intValue = intent.getIntExtra(EXTRA_KEY, PlaybackStore.key.intValue).coerceIn(-12, 12)
                applyPitch()
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(progressTick)
        mediaPlayer?.release()
        mediaSession.release()
        super.onDestroy()
    }

    private fun playTrack(track: Track) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@PlaybackService, track.uri)
            setOnCompletionListener {
                PlaybackStore.isPlaying.value = false
                next()
            }
            prepare()
            PlaybackStore.durationMs.intValue = duration
            PlaybackStore.positionMs.intValue = 0
            PlaybackStore.key.intValue = 0
            applyPitch()
            start()
        }
        PlaybackStore.currentTrack.value = track
        PlaybackStore.isPlaying.value = true
        startAsForeground()
    }

    private fun resume() {
        mediaPlayer?.start()
        PlaybackStore.isPlaying.value = mediaPlayer != null
        updateNotification()
    }

    private fun pause() {
        mediaPlayer?.pause()
        PlaybackStore.isPlaying.value = false
        updateNotification()
    }

    private fun previous() {
        val queue = PlaybackStore.queue
        if (queue.isEmpty()) return
        val nextIndex = (PlaybackStore.queueIndex - 1).coerceAtLeast(0)
        PlaybackStore.queueIndex = nextIndex
        playTrack(queue[nextIndex])
    }

    private fun next() {
        val queue = PlaybackStore.queue
        if (queue.isEmpty()) return
        val nextIndex = PlaybackStore.queueIndex + 1
        if (nextIndex < queue.size) {
            PlaybackStore.queueIndex = nextIndex
            playTrack(queue[nextIndex])
        } else {
            PlaybackStore.isPlaying.value = false
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.pause()
                player.seekTo(0)
            }
            PlaybackStore.positionMs.intValue = 0
            updateNotification()
        }
    }

    private fun seek(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        PlaybackStore.positionMs.intValue = positionMs
        updateNotification()
    }

    private fun applyPitch() {
        val player = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams
                    .setPitch(keyToPitch(PlaybackStore.key.intValue))
                    .setSpeed(1f)
                player.playbackParams = params
            } catch (e: IllegalStateException) {
                val params = android.media.PlaybackParams()
                    .setPitch(keyToPitch(PlaybackStore.key.intValue))
                    .setSpeed(1f)
                try {
                    player.playbackParams = params
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        updateSession()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        updateSession()
        val track = PlaybackStore.currentTrack.value

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
        val artwork = loadBitmap(track?.artworkPath)
        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder
            // 💡 修正ポイント1: 通知が消える原因となるカスタム画像を避け、
            // Android標準のシステム音楽アイコンを確実に指定して復活させます
            .setSmallIcon(R.drawable.karaplay)
            .setContentTitle(track?.title ?: "KaraPlay")
            .setContentText(track?.artist ?: "再生待機中")
            .setContentIntent(openIntent)
            .setOngoing(PlaybackStore.isPlaying.value)
            // 💡 修正ポイント2: .addAction(...) はすべて削除します。
            // Android 11以降の端末では、これだけでOSがPlaybackStateからボタンを勝手に作ってくれます。
            // Android 10以前の互換性を考慮し、コンパクトビューのインデックス指定だけ残します。
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateSession() {
        val track = PlaybackStore.currentTrack.value
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "KaraPlay")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, PlaybackStore.durationMs.intValue.toLong())

        val artwork = loadBitmap(track?.artworkPath)
        if (artwork != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
        }

        mediaSession.setMetadata(metadataBuilder.build())

        // 💡 状態の同期を強化
        val state = if (PlaybackStore.isPlaying.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

        // 💡 修正ポイント3: カスタム操作に対応した標準アクションを漏れなくセッションに詰め込みます
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SEEK_TO
                )
                // 現在の再生位置(positionMs)を通知側に正確に同期させます
                .setState(state, PlaybackStore.positionMs.intValue.toLong(), 1f)
                .build()
        )
    }

    private fun serviceIntent(action: String): PendingIntent {
        return PendingIntent.getService(this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action), pendingFlags())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "再生", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun Intent.toTrack(): Track {
        return Track(
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            artist = getStringExtra(EXTRA_ARTIST).orEmpty(),
            uri = Uri.parse(getStringExtra(EXTRA_URI).orEmpty()),
            source = getStringExtra(EXTRA_SOURCE).orEmpty(),
            artworkPath = getStringExtra(EXTRA_ARTWORK)
        )
    }

    companion object {
        const val ACTION_PLAY_TRACK = "net.ogatomo.karaplay.PLAY_TRACK"
        const val ACTION_TOGGLE = "net.ogatomo.karaplay.TOGGLE"
        const val ACTION_PAUSE = "net.ogatomo.karaplay.PAUSE"
        const val ACTION_PREVIOUS = "net.ogatomo.karaplay.PREVIOUS"
        const val ACTION_NEXT = "net.ogatomo.karaplay.NEXT"
        const val ACTION_SEEK = "net.ogatomo.karaplay.SEEK"
        const val ACTION_SET_KEY = "net.ogatomo.karaplay.SET_KEY"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_URI = "uri"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_ARTWORK = "artwork"
        const val EXTRA_POSITION = "position"
        const val EXTRA_KEY = "key"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
    }
}

fun Context.startPlayback(track: Track, queue: List<Track>) {
    PlaybackStore.queue = queue
    PlaybackStore.queueIndex = queue.indexOfFirst { it.uri == track.uri }
    val intent = Intent(this, PlaybackService::class.java)
        .setAction(PlaybackService.ACTION_PLAY_TRACK)
        .putExtra(PlaybackService.EXTRA_TITLE, track.title)
        .putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
        .putExtra(PlaybackService.EXTRA_URI, track.uri.toString())
        .putExtra(PlaybackService.EXTRA_SOURCE, track.source)
        .putExtra(PlaybackService.EXTRA_ARTWORK, track.artworkPath)
    startKaraPlayService(intent)
}

fun Context.sendPlaybackAction(action: String) {
    startService(Intent(this, PlaybackService::class.java).setAction(action))
}

fun Context.seekPlayback(positionMs: Int) {
    startService(
        Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_SEEK)
            .putExtra(PlaybackService.EXTRA_POSITION, positionMs)
    )
}

fun Context.setPlaybackKey(key: Int) {
    PlaybackStore.key.intValue = key.coerceIn(-12, 12)
    startService(
        Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_SET_KEY)
            .putExtra(PlaybackService.EXTRA_KEY, key)
    )
}

fun Context.startKaraPlayService(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
}

fun pendingFlags(): Int {
    return PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
