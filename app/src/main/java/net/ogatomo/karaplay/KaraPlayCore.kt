package net.ogatomo.karaplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.net.URLDecoder
import kotlin.math.pow

data class Track(
    val title: String,
    val artist: String,
    val uri: Uri,
    val source: String,
    val artworkPath: String? = null
)

data class FileMeta(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val source: String
)

object PlaybackStore {
    val currentTrack = mutableStateOf<Track?>(null)
    val isPlaying = mutableStateOf(false)
    val durationMs = mutableIntStateOf(0)
    val positionMs = mutableIntStateOf(0)
    val key = mutableIntStateOf(0)
    var queue: List<Track> = emptyList()
    var queueIndex: Int = -1
}

object DownloadStore {
    val isRunning = mutableStateOf(false)
    val progress = mutableFloatStateOf(0f)
    val status = mutableStateOf("準備完了")
    val ytdlpVersion = mutableStateOf("確認中")
    val logs = mutableStateListOf<String>()

    fun log(message: String) {
        logs.add(0, message)
        if (logs.size > 80) logs.removeRange(80, logs.size)
    }
}

fun ensureKaraPlayFolder(): File {
    val dir = downloadFolder()
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun downloadFolder(): File = File(Environment.getExternalStorageDirectory(), "KaraPlay")

fun scanDocumentTreesForMeta(
    context: Context,
    treeUris: List<String>,
    onPermissionDenied: (String) -> Unit
): List<FileMeta> {
    val results = mutableListOf<FileMeta>()

    treeUris.forEach { uriText ->
        val uri = Uri.parse(uriText)
        // 💡 1. そもそもシステムが「権限を保持していない」と判断している場合
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uriText && it.isReadPermission
        }

        if (!hasPermission) {
            onPermissionDenied(uriText)
            return@forEach
        }

        // 💡 2. fromTreeUri が null 、もしくは root が正常に取得できない場合
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.exists() || !root.canRead()) {
            onPermissionDenied(uriText)
            return@forEach
        }

        val metaList = scanDocumentTreeForMeta(context, uri)
        if (metaList.isEmpty() && root.listFiles() == null) {
            // 💡 3. リストの取得自体がシステムに拒否されて null になっている場合
            onPermissionDenied(uriText)
        } else {
            results.addAll(metaList)
        }
    }

    return results.distinctBy { it.uri.toString() }
}

fun scanNativeFolderForMeta(context: Context, dir: File, sourceName: String): List<FileMeta> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    val results = mutableListOf<FileMeta>()
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            results.addAll(scanNativeFolderForMeta(context, file, sourceName))
        } else if (file.isFile && isAudioFile(file.name)) {
            results.add(FileMeta(Uri.fromFile(file), file.nameWithoutExtension, file.lastModified(), sourceName))
        }
    }
    return results
}

private fun scanDocumentTreeForMeta(context: Context, treeUri: Uri): List<FileMeta> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    // root.name の取得失敗やエラーを考慮
    val sourceName = root.name ?: "ローカル"

    // listFiles() が null（権限エラー等）なら即空リストを返す
    val files = root.listFiles() ?: return emptyList()

    return files.flatMap { child -> scanDocumentFileForMeta(child, sourceName) }
}

private fun scanDocumentFileForMeta(file: DocumentFile, sourceName: String): List<FileMeta> {
    if (file.isFile) {
        val name = file.name.orEmpty()
        return if (isAudioFile(name)) {
            val defaultTitle = name.substringBeforeLast('.')
            listOf(FileMeta(file.uri, defaultTitle, file.lastModified(), "ローカル"))
        } else {
            emptyList()
        }
    }
    if (!file.isDirectory) return emptyList()
    return file.listFiles().flatMap { child -> scanDocumentFileForMeta(child, sourceName) }
}

fun scanDownloadFolderForMeta(): List<FileMeta> {
    val dir = ensureKaraPlayFolder()
    return dir.listFiles()
        ?.filter { it.isFile && isAudioFile(it.name) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
            val baseName = file.nameWithoutExtension
            FileMeta(Uri.fromFile(file), baseName, file.lastModified(), "ダウンロード")
        }
        ?: emptyList()
}

fun isAllowedYoutubeUrl(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
}

fun isAudioFile(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") ||
        lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".ogg")
}

fun findArtworkFor(audioFile: File): File? {
    val base = audioFile.nameWithoutExtension
    return audioFile.parentFile?.listFiles()?.firstOrNull {
        it.isFile && it.nameWithoutExtension == base && isImageFile(it.name)
    }
}

fun isImageFile(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
}

fun cropImageFileToSquare(file: File) {
    try {
        val source = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val side = minOf(source.width, source.height)
        if (side <= 0) {
            source.recycle()
            return
        }
        val cropped = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        canvas.drawBitmap(source, ((side - source.width) / 2f), ((side - source.height) / 2f), null)
        file.outputStream().use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        source.recycle()
        cropped.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun cropDownloadedThumbnails(dir: File) {
    dir.listFiles()?.filter { isImageFile(it.name) }?.forEach { image ->
        cropImageFileToSquare(image)
    }
}

fun extractTrackMetadata(context: Context, uri: Uri, defaultTitle: String, defaultArtist: String, source: String, lastModified: Long): Track {
    val retriever = MediaMetadataRetriever()
    var title = defaultTitle
    var artist = defaultArtist
    var artworkPath: String? = null

    try {
        retriever.setDataSource(context, uri)
        val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (!extractedTitle.isNullOrBlank()) {
            title = extractedTitle
        }
        val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        if (!extractedArtist.isNullOrBlank()) {
            artist = extractedArtist
        }

        val picture = retriever.embeddedPicture
        if (picture != null) {
            val cacheDir = context.cacheDir
            // 💡 修正: キャッシュ名にハッシュと更新日時を含めて、変更がない限り再利用する
            val uriStr = uri.toString()
            val tempFile = File(cacheDir, "art_${uriStr.hashCode()}_${lastModified}.jpg")
            if (!tempFile.exists()) {
                tempFile.outputStream().use { out -> out.write(picture) }
                cropImageFileToSquare(tempFile)
            }
            artworkPath = tempFile.absolutePath
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {}
    }

    if (artworkPath == null) {
        if (uri.scheme == "file") {
            try {
                // 💡 修正: URLデコードを挟んで、スペースや日本語のパスでもファイルを見つけられるようにする
                val decodedPath = URLDecoder.decode(uri.path.orEmpty(), "UTF-8")
                val file = File(decodedPath)
                if (file.exists()) {
                    artworkPath = findArtworkFor(file)?.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    return Track(
        title = title,
        artist = artist,
        uri = uri,
        source = source,
        artworkPath = artworkPath
    )
}

fun formatDocumentTreeUri(uriString: String): String {
    return try {
        val decoded = URLDecoder.decode(uriString, "UTF-8")
        val treeId = decoded.substringAfter("/tree/", "")
        if (treeId.isEmpty()) return uriString
        
        val parts = treeId.split(":")
        if (parts.size == 2) {
            val storageType = parts[0]
            val relativePath = parts[1]
            if (storageType == "primary") {
                "/storage/emulated/0/$relativePath"
            } else {
                "/storage/$storageType/$relativePath"
            }
        } else {
            if (treeId.startsWith("primary")) {
                "/storage/emulated/0"
            } else {
                treeId
            }
        }
    } catch (e: Exception) {
        uriString
    }
}

fun loadBitmap(path: String?): Bitmap? {
    if (path == null) return null
    return BitmapFactory.decodeFile(path)
}

fun keyToPitch(key: Int): Float = 2.0.pow(key / 12.0).toFloat()

fun keyLabel(key: Int): String = when {
    key > 0 -> "+$key"
    key < 0 -> key.toString()
    else -> "原"
}
