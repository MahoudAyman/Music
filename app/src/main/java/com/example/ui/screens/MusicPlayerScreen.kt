package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.MusicPlayerController
import com.example.music.Song
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen() {
    val context = LocalContext.current
    val controller = remember { MusicPlayerController.getInstance(context) }
    
    var hasPermission by remember { mutableStateOf(false) }
    
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.READ_MEDIA_AUDIO] == true || result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            controller.loadLocalSongs()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // we no longer release controller on dispose to allow background play
            // controller.release()
        }
    }

    val playlist by controller.playlist.collectAsState()
    val currentSong by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val progressMs by controller.progressMs.collectAsState()
    val shuffleMode by controller.shuffleMode.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val smartNotification by controller.smartNotification.collectAsState()

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ParticleBackground(isPlaying)
        if (!hasPermission) {
            PermissionScreen(onClick = { launcher.launch(permissions) })
        } else if (playlist.isEmpty()) {
            EmptyScreen()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Giant Header
                HeaderTopBar(
                    currentPage = pagerState.currentPage,
                    onPageSelected = { page ->
                        coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> LibraryScreen(playlist, currentSong, isPlaying) { controller.playSong(it) }
                        1 -> PlayerScreen(controller, currentSong, isPlaying, progressMs, shuffleMode, repeatMode)
                        2 -> AudioFxScreen(isPlaying)
                    }
                }
            }
            
            // Smart Notification Overlay
            AnimatedVisibility(
                visible = smartNotification != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        smartNotification ?: "",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 3D Box
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    rotationX = 30f
                    rotationY = 30f
                }
                .background(
                    Brush.radialGradient(listOf(Color(0xFF00FF7F), Color.Transparent)),
                    alpha = 0.5f
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("الصلاحيات مطلوبة للوصول إلى الموسيقى", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("منح الصلاحية", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(24.dp))
        Text("لم يتم العثور على أغانٍ", style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
fun HeaderTopBar(currentPage: Int, onPageSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val titles = listOf("المكتبة", "المشغل", "المؤثرات")
        titles.forEachIndexed { index, title ->
            val isSelected = currentPage == index
            val color by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
            val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f)
            
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = color,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { onPageSelected(index) }
            )
        }
    }
}

@Composable
fun LibraryScreen(playlist: List<Song>, currentSong: Song?, isPlaying: Boolean, onSongClick: (Song) -> Unit) {
    var selectedSection by remember { mutableStateOf("Songs") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Songs" to "الأغاني", "Artists" to "الفنانين", "Albums" to "الألبومات").forEach { (id, title) ->
                val isSelected = selectedSection == id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        .clickable { selectedSection = id }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(title, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (selectedSection == "Songs") {
                items(playlist) { song ->
                    val isCurrent = song == currentSong
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { onSongClick(song) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Floating Avatar
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        if (isCurrent) listOf(Color(0xFF00FF7F), Color(0xFF00BFFF))
                                        else listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent && isPlaying) {
                                AnimatedEqualizerIcon()
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (isCurrent) Color.Black else Color.Gray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (selectedSection == "Artists") {
                val artists = playlist.groupBy { it.artist }.keys.toList()
                items(artists) { artist ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(artist, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            } else if (selectedSection == "Albums") {
                val albums = playlist.groupBy { it.album }.keys.toList()
                items(albums) { album ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(album, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEqualizerIcon() {
    val infiniteTransition = rememberInfiniteTransition()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(modifier = Modifier.width(4.dp).height(24.dp * height).background(Color.Black))
        }
    }
}

@Composable
fun PlayerScreen(
    controller: MusicPlayerController,
    currentSong: Song?,
    isPlaying: Boolean,
    progressMs: Long,
    shuffleMode: Boolean,
    repeatMode: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.1f))
        
        // Giant 3D Disc Display
        ThreeDVinylDisplay(isPlaying)
        
        Spacer(modifier = Modifier.weight(0.1f))
        
        // Song Info
        Text(
            currentSong?.title ?: "Unknown Title",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            currentSong?.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Advanced Interactive Progress Bar
        val duration = currentSong?.durationMs ?: 1L
        var dragProgress by remember { mutableStateOf<Float?>(null) }
        val displayProgress = dragProgress ?: (progressMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragProgress?.let { ratio ->
                                controller.seekTo((ratio * duration).toLong())
                            }
                            dragProgress = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                drawRect(color = Color.White.copy(alpha = 0.1f))
                drawRect(
                    Brush.horizontalGradient(listOf(Color(0xFF00BFFF), Color(0xFF00FF7F))),
                    size = Size(size.width * displayProgress, size.height)
                )
            }
            // Giant Neon Thumb
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .offset(x = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 48.dp) * displayProgress)
                        .size(20.dp)
                        .graphicsLayer { shadowElevation = 16.dp.toPx() }
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.CenterStart)
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(progressMs), color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Text(formatTime(duration), color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Giant Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle, contentDescription = null,
                    tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            IconButton(onClick = { controller.playPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }

            // Massive Play Button
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        shadowElevation = if (isPlaying) 0f else 32.dp.toPx()
                    }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFF2A5F), Color(0xFFC00030))))
                    .clickable { controller.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            IconButton(onClick = { controller.playNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }

            IconButton(onClick = { controller.toggleRepeat() }) {
                Icon(
                    when (repeatMode) {
                        1 -> Icons.Default.RepeatOn
                        2 -> Icons.Default.RepeatOneOn
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = null,
                    tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ThreeDVinylDisplay(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Floating effect
    val floatY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(320.dp)
            .graphicsLayer {
                translationY = if (isPlaying) floatY else 0f
            },
        contentAlignment = Alignment.Center
    ) {
        // glowing aura
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer {
            val scale = if (isPlaying) 1.2f else 1f
            scaleX = scale; scaleY = scale
            alpha = if (isPlaying) 0.6f else 0f
        }) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF00FF7F).copy(alpha = 0.5f), Color.Transparent)),
                radius = size.width / 2
            )
        }

        // The Disc
        Box(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    rotationX = 30f // 3D tilt
                    rotationY = if (isPlaying) (floatY * 0.5f) else 0f
                    rotationZ = if (isPlaying) angle else 0f
                    shadowElevation = 64.dp.toPx()
                    shape = CircleShape
                    clip = true
                }
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Realistic Grooves
                for (i in 1..20) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f + (i * 0.002f)),
                        radius = (size.width / 2) - (i * 6.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                
                // Light Reflection Sweep
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true
                )
            }
            
            // Holographic Center Label
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Magenta, Color.Cyan, Color.Yellow, Color.Magenta))),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF0A0E17)))
            }
        }
    }
}

@Composable
fun AudioFxScreen(isPlaying: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("المعادل الصوتي العملاق", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("3D Audio Space & Equalizer", color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.weight(1f))
        
        // Massive 3D Equalizer
        GiantEqualizer(isPlaying)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 3D Spatial Audio Knobs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RotaryKnob("Bass")
            RotaryKnob("Surround")
            RotaryKnob("Treble")
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun GiantEqualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val bands = 8
    
    Row(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until bands) {
            val state by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = if (isPlaying) 1f else 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + (Math.random() * 500).toInt(), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight(state)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF00FF7F), Color(0xFF00BFFF), Color(0xFFFF2A5F))
                        )
                    )
            )
        }
    }
}

@Composable
fun RotaryKnob(label: String) {
    var rotation by remember { mutableStateOf(140f) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { shadowElevation = 16.dp.toPx() }
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E))
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        rotation = (rotation + dragAmount.y).coerceIn(40f, 320f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // outer ring
                drawCircle(color = Color(0xFF333333), style = Stroke(width = 8.dp.toPx()))
                // value arc
                drawArc(
                    color = Color(0xFF00FF7F),
                    startAngle = 140f,
                    sweepAngle = (rotation - 140f),
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // Dial Indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.BottomStart)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ParticleBackground(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart)
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        for (i in 0 until 50) {
            val xOffset = (Math.sin((time + i * 100) / 100.0) * w).toFloat() % w
            val yOffset = ((time * (i % 5 + 1) * 2) % h)
            val finalX = if (xOffset < 0) xOffset + w else xOffset
            val finalY = h - yOffset
            val speedAlpha = if (isPlaying) 0.5f else 0.1f
            drawCircle(
                color = Color(0xFF00FF7F).copy(alpha = (((i % 5)/5f) * speedAlpha)),
                radius = (i % 4 + 2).dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(finalX, finalY)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

