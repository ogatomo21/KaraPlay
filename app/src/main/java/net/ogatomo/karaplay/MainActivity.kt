package net.ogatomo.karaplay

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ogatomo.karaplay.ui.theme.OgaTomoTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrlState.value = extractSharedUrl(intent)
        setContent {
            val prefs = remember { getSharedPreferences("karaplay", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            OgaTomoTheme(darkTheme = darkTheme) {
                SetSystemBarsColor(
                    statusBarColor = MaterialTheme.colorScheme.background,
                    navigationBarColor = MaterialTheme.colorScheme.surfaceContainer,
                    isLightIcons = !darkTheme
                )
                KaraPlayApp(
                    prefs = prefs,
                    sharedUrl = sharedUrlState.value,
                    onSharedUrlConsumed = { sharedUrlState.value = null },
                    onThemeChanged = { themeMode = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUrlState.value = extractSharedUrl(intent)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.split(Regex("\\s+"))
            ?.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Player("再生", Icons.Default.PlayArrow),
    Local("ローカル", Icons.Default.LibraryMusic),
    Download("ダウンロード", Icons.Default.Download),
    Settings("設定", Icons.Default.Settings)
}

@Composable
private fun KaraPlayApp(
    prefs: SharedPreferences,
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    onThemeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(Tab.Player) }
    var downloadUrl by remember { mutableStateOf("") }
    var showDownloader by remember { mutableStateOf(false) }
    var localVisible by remember { mutableStateOf(prefs.getBoolean("local_visible", true)) }
    var downloadVisible by remember { mutableStateOf(prefs.getBoolean("download_visible", true)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    val localFolders = remember {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet("local_trees", emptySet()).orEmpty()) }
    }
    val localTracks = remember { mutableStateListOf<Track>() }
    val downloadTracks = remember { mutableStateListOf<Track>() }
    var isLocalLoading by remember { mutableStateOf(false) }
    var isDownloadLoading by remember { mutableStateOf(false) }

    fun saveLocalFolders() {
        prefs.edit().putStringSet("local_trees", localFolders.toSet()).apply()
    }

    fun refreshLocal() {
        isLocalLoading = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val dbHelper = TrackDatabaseHelper(context)
                val cachedPairs = dbHelper.getCachedTracks("ローカル").sortedBy { it.second.title.lowercase() }
                withContext(Dispatchers.Main) {
                    localTracks.clear()
                    localTracks += cachedPairs.map { it.second }
                }

                val docsMeta = scanDocumentTreesForMeta(context, localFolders) { invalidUriText ->
                    coroutineScope.launch(Dispatchers.Main) {
                        if (localFolders.remove(invalidUriText)) {
                            saveLocalFolders()
                            Toast.makeText(context, "アクセス権限の切れたフォルダを自動除外しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val musicFolder = File(Environment.getExternalStorageDirectory(), "Music")
                val nativesMeta = scanNativeFolderForMeta(context, musicFolder, "Music")
                val allMeta = (docsMeta + nativesMeta).distinctBy { it.uri.toString() }

                val cachedMap = cachedPairs.associateBy { it.second.uri.toString() }
                val currentUriStrs = mutableSetOf<String>()
                var changed = false
                val updatedList = localTracks.toMutableList()

                allMeta.forEach { meta ->
                    val uriStr = meta.uri.toString()
                    currentUriStrs.add(uriStr)
                    val cached = cachedMap[uriStr]

                    if (cached == null || cached.first != meta.lastModified || (cached.second.artworkPath != null && !File(cached.second.artworkPath!!).exists())) {
                        val parts = meta.name.split(" - ", limit = 2)
                        val defaultTitle = parts.getOrNull(0) ?: meta.name
                        val defaultArtist = parts.getOrNull(1) ?: ""

                        val track = extractTrackMetadata(
                            context = context, uri = meta.uri,
                            defaultTitle = defaultTitle, defaultArtist = defaultArtist,
                            source = meta.source, lastModified = meta.lastModified
                        )
                        dbHelper.insertOrUpdateTrack(uriStr, meta.lastModified, track)
                        changed = true

                        val idx = updatedList.indexOfFirst { it.uri.toString() == uriStr }
                        if (idx >= 0) updatedList[idx] = track else updatedList.add(track)
                    }
                }

                cachedMap.keys.forEach { uriStr ->
                    if (!currentUriStrs.contains(uriStr)) {
                        dbHelper.deleteTrack(uriStr)
                        changed = true
                        updatedList.removeAll { it.uri.toString() == uriStr }
                    }
                }

                val finalSortedList = if (changed) updatedList.sortedBy { it.title.lowercase() } else null

                withContext(Dispatchers.Main) {
                    if (finalSortedList != null) {
                        localTracks.clear()
                        localTracks += finalSortedList
                    }
                    isLocalLoading = false
                }
            }
        }
    }

    fun refreshDownloads() {
        val dbHelper = TrackDatabaseHelper(context)
        isDownloadLoading = true
        coroutineScope.launch {
            val cachedPairs = withContext(Dispatchers.IO) {
                dbHelper.getCachedTracks("ダウンロード").sortedByDescending { it.first }
            }
            downloadTracks.clear()
            downloadTracks += cachedPairs.map { it.second }
            if (downloadTracks.isNotEmpty()) {
                isDownloadLoading = false
            }

            withContext(Dispatchers.IO) {
                val allMeta = scanDownloadFolderForMeta()
                val cachedMap = dbHelper.getCachedTracks("ダウンロード").associateBy { it.second.uri.toString() }
                val currentUriStrs = mutableSetOf<String>()
                var changed = false
                val pendingTracks = mutableListOf<Track>()

                allMeta.forEach { meta ->
                    val uriStr = meta.uri.toString()
                    currentUriStrs.add(uriStr)
                    val cached = cachedMap[uriStr]

                    if (cached == null || cached.first != meta.lastModified || (cached.second.artworkPath != null && !File(cached.second.artworkPath!!).exists())) {
                        val parts = meta.name.split(" - ", limit = 2)
                        val defaultTitle = parts.getOrNull(0) ?: meta.name
                        val defaultArtist = parts.getOrNull(1) ?: ""

                        val track = extractTrackMetadata(
                            context = context, uri = meta.uri,
                            defaultTitle = defaultTitle, defaultArtist = defaultArtist,
                            source = meta.source, lastModified = meta.lastModified
                        )
                        dbHelper.insertOrUpdateTrack(uriStr, meta.lastModified, track)
                        changed = true

                        pendingTracks.add(track)
                        if (pendingTracks.size >= 10) {
                            val batch = pendingTracks.toList()
                            pendingTracks.clear()
                            withContext(Dispatchers.Main) {
                                batch.forEach { t ->
                                    val idx = downloadTracks.indexOfFirst { it.uri.toString() == t.uri.toString() }
                                    if (idx >= 0) downloadTracks[idx] = t else downloadTracks.add(t)
                                }
                                isDownloadLoading = false
                            }
                        }
                    }
                }

                if (pendingTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        pendingTracks.forEach { t ->
                            val idx = downloadTracks.indexOfFirst { it.uri.toString() == t.uri.toString() }
                            if (idx >= 0) downloadTracks[idx] = t else downloadTracks.add(t)
                        }
                        isDownloadLoading = false
                    }
                }

                cachedMap.keys.forEach { uriStr ->
                    if (!currentUriStrs.contains(uriStr)) {
                        dbHelper.deleteTrack(uriStr)
                        changed = true
                        withContext(Dispatchers.Main) {
                            downloadTracks.removeAll { it.uri.toString() == uriStr }
                        }
                    }
                }

                if (changed) {
                    val finalSorted = dbHelper.getCachedTracks("ダウンロード").sortedByDescending { it.first }.map { it.second }
                    withContext(Dispatchers.Main) {
                        downloadTracks.clear()
                        downloadTracks += finalSorted
                    }
                }

                withContext(Dispatchers.Main) {
                    isDownloadLoading = false
                }
            }
        }
    }

    fun play(track: Track, tracks: List<Track>) {
        context.startPlayback(track, tracks)
        selectedTab = Tab.Player
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val uriText = uri.toString()
            if (!localFolders.contains(uriText)) {
                localFolders.add(uriText)
                saveLocalFolders()
            }
            refreshLocal()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshLocal()
        refreshDownloads()
    }

    LaunchedEffect(Unit) {
        ensureKaraPlayFolder()
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT < 33) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
        }

        context.updateYtDlp()
        refreshLocal()
        refreshDownloads()
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            downloadUrl = sharedUrl
            selectedTab = Tab.Download
            showDownloader = true
            onSharedUrlConsumed()
        }
    }

    val isDownloading = DownloadStore.isRunning.value
    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            refreshDownloads()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab.entries.filter {
                    (it != Tab.Local || localVisible) && (it != Tab.Download || downloadVisible)
                }.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                Tab.Player -> PlayerScreen()
                Tab.Local -> TrackListScreen(
                    title = "ローカル",
                    subtitle = "${localFolders.size+1} フォルダ / ${localTracks.size} 曲",
                    tracks = localTracks,
                    emptyText = "フォルダを追加するとサブフォルダ内の曲も表示されます",
                    actionLabel = "フォルダを追加",
                    actionIcon = Icons.Default.Folder,
                    isLoading = isLocalLoading,
                    onAction = { folderPicker.launch(null) },
                    onRefresh = { refreshLocal() },
                    onTrackClick = { play(it, localTracks) }
                )
                Tab.Download -> DownloadScreen(
                    tracks = downloadTracks,
                    isLoading = isDownloadLoading,
                    onRefresh = { refreshDownloads() },
                    onOpenDownloader = { showDownloader = true },
                    onTrackClick = { play(it, downloadTracks) },
                    onDeleteTrack = { track ->
                        track.uri.path?.let { pathString ->
                            val file = File(pathString)
                            if (file.exists() && file.delete()) {
                                refreshDownloads()
                            }
                        }
                    }
                )
                Tab.Settings -> SettingsScreen(
                    localVisible = localVisible,
                    downloadVisible = downloadVisible,
                    localFolders = localFolders,
                    themeMode = themeMode,
                    onLocalVisibleChanged = {
                        localVisible = it
                        prefs.edit().putBoolean("local_visible", it).apply()
                        if (!it && selectedTab == Tab.Local) selectedTab = Tab.Player
                    },
                    onDownloadVisibleChanged = {
                        downloadVisible = it
                        prefs.edit().putBoolean("download_visible", it).apply()
                        if (!it && selectedTab == Tab.Download) selectedTab = Tab.Player
                    },
                    onPickFolder = { folderPicker.launch(null) },
                    onRemoveFolder = {
                        localFolders.remove(it)
                        saveLocalFolders()
                        refreshLocal()
                    },
                    onThemeChanged = {
                        themeMode = it
                        prefs.edit().putString("theme", it).apply()
                        onThemeChanged(it)
                    },
                    onUpdateYtDlp = { context.updateYtDlp() }
                )
            }
        }
    }

    if (showDownloader) {
        DownloaderSheet(
            url = downloadUrl,
            onUrlChanged = { downloadUrl = it },
            onDismiss = { showDownloader = false },
            onDownload = {
                context.startDownload(downloadUrl.trim())
                showDownloader = false
            }
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun PlayerScreen() {
    val context = LocalContext.current
    val track = PlaybackStore.currentTrack.value
    val isPlaying = PlaybackStore.isPlaying.value
    val duration = PlaybackStore.durationMs.intValue
    val position = PlaybackStore.positionMs.intValue.coerceIn(0, duration.coerceAtLeast(0))
    val key = PlaybackStore.key.intValue
    var seekValue by remember(position, duration) { mutableIntStateOf(position) }

    // スクロール状態を管理する変数を追加
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(scrollState), // 全体をスクロール可能に変更
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally // コンポーネントを中央揃え
    ) {
        // アートワーク（画面幅に合わせつつ少し縮小し、大きすぎる表示を防ぐ）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            val artwork = remember(track?.artworkPath) { loadBitmap(track?.artworkPath) }
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = "ジャケット",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 曲名・アーティスト名
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = track?.title ?: "曲が選択されていません",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = track?.artist ?: "ローカルまたはダウンロードから選択してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }

        Spacer(Modifier.height(4.dp))

        // シークバーと再生時間
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = seekValue.toFloat(),
                onValueChange = { seekValue = it.toInt() },
                onValueChangeFinished = { context.seekPlayback(seekValue) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = track != null,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(seekValue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text(text = formatTime(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        // コントロールボタン群（再生・キー操作）
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = { context.sendPlaybackAction(PlaybackService.ACTION_PREVIOUS) },
                    enabled = track != null,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "前の曲", modifier = Modifier.size(24.dp))
                }

                Button(
                    onClick = { context.sendPlaybackAction(PlaybackService.ACTION_TOGGLE) },
                    enabled = track != null,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledIconButton(
                    onClick = { context.sendPlaybackAction(PlaybackService.ACTION_NEXT) },
                    enabled = track != null,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "次の曲", modifier = Modifier.size(24.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = track != null) { context.setPlaybackKey(0) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "キー",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = keyLabel(key),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (key != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledIconButton(
                    onClick = { context.setPlaybackKey((key - 1).coerceAtLeast(-12)) },
                    enabled = track != null,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "キーを下げる", modifier = Modifier.size(20.dp))
                }

                FilledIconButton(
                    onClick = { context.setPlaybackKey((key + 1).coerceAtMost(12)) },
                    enabled = track != null,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "キーを上げる", modifier = Modifier.size(20.dp))
                }
            }
        }

        // スクロールレイアウト内での weight はエラーになるため、固定の Spacer に変更
        Spacer(Modifier.height(16.dp))

        // 下部キースライダー
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = key.toFloat(),
                    onValueChange = { context.setPlaybackKey(it.toInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    enabled = track != null // トラックがない場合はスライダーも無効化
                )
            }
        }
    }
}

@Composable
private fun TrackListScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    emptyText: String,
    actionLabel: String,
    actionIcon: ImageVector,
    isLoading: Boolean,
    onAction: () -> Unit,
    onRefresh: () -> Unit,
    onTrackClick: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "更新") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Icon(actionIcon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(actionLabel)
        }
        Spacer(Modifier.height(16.dp))

        if (tracks.isEmpty() && isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn {
                items(tracks) { track ->
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            val subtitleText = track.artist.ifBlank { track.uri.lastPathSegment ?: "ローカル" }
                            Text(subtitleText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            val artwork = remember(track.artworkPath) { loadBitmap(track.artworkPath) }
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                if (artwork != null) {
                                    Image(bitmap = artwork.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        },
                        modifier = Modifier.clickable { onTrackClick(track) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DownloadScreen(
    tracks: List<Track>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenDownloader: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit
) {
    var showLogSheet by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ダウンロード", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${tracks.size} 曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Row {
                IconButton(onClick = { showLogSheet = true }) { Icon(Icons.Default.List, contentDescription = "ログを表示") }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "更新") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenDownloader, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("URL から追加")
        }

        if (DownloadStore.isRunning.value || DownloadStore.progress.floatValue > 0f) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { (DownloadStore.progress.floatValue / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (DownloadStore.status.value.isNotEmpty()) {
            Text(DownloadStore.status.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tracks) { track ->
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            val subtitleText = track.artist.ifBlank { track.uri.lastPathSegment ?: "ダウンロード" }
                            Text(subtitleText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            val artwork = remember(track.artworkPath) { loadBitmap(track.artworkPath) }
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                if (artwork != null) {
                                    Image(bitmap = artwork.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { onTrackClick(track) },
                            onLongClick = { trackToDelete = track }
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (trackToDelete != null) {
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("曲の削除") },
            text = { Text("「${trackToDelete?.title}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    trackToDelete?.let { onDeleteTrack(it) }
                    trackToDelete = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) { Text("キャンセル") }
            }
        )
    }

    if (showLogSheet) {
        ModalBottomSheet(onDismissRequest = { showLogSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("yt-dlp 実行ログ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showLogSheet = false }) { Text("閉じる") }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (DownloadStore.logs.isEmpty()) {
                        item { Text("ログはありません", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                    } else {
                        items(DownloadStore.logs) { logLine ->
                            Text(logLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    localVisible: Boolean,
    downloadVisible: Boolean,
    localFolders: List<String>,
    themeMode: String,
    onLocalVisibleChanged: (Boolean) -> Unit,
    onDownloadVisibleChanged: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onThemeChanged: (String) -> Unit,
    onUpdateYtDlp: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("タブの表示設定", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SettingRow("ローカルタブを表示", "指定フォルダの音源一覧を表示します", localVisible, onLocalVisibleChanged)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingRow("ダウンロードタブを表示", "取得済み音源とダウンローダーを表示します", downloadVisible, onDownloadVisibleChanged)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ローカルフォルダ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Music (内蔵ストレージ)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(text = "システム標準", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }

                    if (localFolders.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }

                    localFolders.forEach { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(text = formatDocumentTreeUri(folder), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onRemoveFolder(folder) }) { Text("削除", color = MaterialTheme.colorScheme.error) }
                        }
                    }

                    Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("フォルダを追加")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ダークモード", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    // 選択肢のデータを定義
                    val themeOptions = listOf(
                        Triple("system", "システム", Icons.Default.Home),
                        Triple("light", "ライト", Icons.Default.LightMode),
                        Triple("dark", "ダーク", Icons.Default.DarkMode)
                    )

                    // 単一選択のセグメントボタン行
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, option ->
                            val (value, label, icon) = option
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                onClick = { onThemeChanged(value) },
                                selected = themeMode == value,
                                icon = {
                                    // デフォルトのチェックマークアニメーションを避け、常にアイコンを表示させます
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("システム", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("yt-dlp バージョン: ${DownloadStore.ytdlpVersion.value}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onUpdateYtDlp, enabled = !DownloadStore.isRunning.value, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("コア更新")
                        }
                        FilledTonalButton(onClick = { openOssLicenses(context) }, modifier = Modifier.weight(1f)) {
                            Text("ライセンス")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderSheet(
    url: String,
    onUrlChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("YouTube から音源を追加", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube URL") },
                singleLine = true
            )
            Text("保存形式は常に m4a です。\n保存先: ${downloadFolder().absolutePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("閉じる") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("追加する")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun openOssLicenses(context: Context) {
    try {
        val intent = Intent(context, OpenSourceLicensesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "ライセンス画面を開けませんでした", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SetSystemBarsColor(
    statusBarColor: androidx.compose.ui.graphics.Color,
    navigationBarColor: androidx.compose.ui.graphics.Color,
    isLightIcons: Boolean
) {
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = statusBarColor,
            darkIcons = isLightIcons
        )
        systemUiController.setNavigationBarColor(
            color = navigationBarColor,
            darkIcons = isLightIcons
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPreview() {
    OgaTomoTheme {
        PlayerScreen()
    }
}