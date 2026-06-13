package net.ogatomo.karaplay

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

class KaraPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        kotlin.concurrent.thread(name = "KaraPlayInit") {
            try {
                YoutubeDL.getInstance().init(this@KaraPlayApplication)
                FFmpeg.getInstance().init(this@KaraPlayApplication)
                ensureKaraPlayFolder()
            } catch (exception: YoutubeDLException) {
                Log.e("KaraPlay", "yt-dlp の初期化に失敗しました", exception)
            } catch (exception: Exception) {
                Log.e("KaraPlay", "KaraPlay の初期化に失敗しました", exception)
            }
        }
    }
}
