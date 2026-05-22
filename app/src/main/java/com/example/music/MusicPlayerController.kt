package com.example.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val durationMs: Long
)

class MusicPlayerController private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: MusicPlayerController? = null
        
        fun getInstance(context: Context): MusicPlayerController {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerController(context.applicationContext).also { instance = it }
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mediaSession = MediaSession(context, "MusicPlayerSession")
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(0) // 0: None, 1: All, 2: One
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var originalPlaylist = emptyList<Song>()

    private val _smartNotification = MutableStateFlow<String?>(null)
    val smartNotification: StateFlow<String?> = _smartNotification.asStateFlow()

    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_isPlaying.value) {
                    togglePlayPause()
                    showSmartNotification("🎧 تم فصل السماعة - تم إيقاف الموسيقى تلقائياً")
                }
            }
        }
    }

    init {
        createNotificationChannel()
        loadLocalSongs()
        context.registerReceiver(
            audioNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_channel",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showSmartNotification(msg: String) {
        _smartNotification.value = msg
        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            if (_smartNotification.value == msg) _smartNotification.value = null
        }
    }

    fun loadLocalSongs() {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        try {
            val cursor = context.contentResolver.query(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown"
                    val album = it.getString(albumColumn) ?: "Unknown"
                    val duration = it.getLong(durationColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs.add(Song(id, title, artist, album, uri, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _playlist.value = songs
        originalPlaylist = songs
        if (songs.isNotEmpty() && _currentSong.value == null) {
            _currentSong.value = songs.first()
        }
    }

    fun playSong(song: Song) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(context, song.uri)
            prepare()
            start()
        }
        _currentSong.value = song
        _isPlaying.value = true
        startProgressTracking()
        updateNotification()

        mediaPlayer?.setOnCompletionListener {
            playNext()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: run {
            _currentSong.value?.let { playSong(it) }
            return
        }
        
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
        updateNotification()
    }

    fun toggleShuffle() {
        val isShuffle = !_shuffleMode.value
        _shuffleMode.value = isShuffle
        if (isShuffle) {
            val shuffled = originalPlaylist.shuffled()
            _playlist.value = shuffled
            showSmartNotification("🔀 تفعيل التبديل العشوائي")
        } else {
            _playlist.value = originalPlaylist
            showSmartNotification("➡️ إيقاف العشوائي")
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
        val msg = when (_repeatMode.value) {
            1 -> "🔁 تكرار القائمة"
            2 -> "🔂 تكرار الأغنية"
            else -> "➡️ إيقاف التكرار"
        }
        showSmartNotification(msg)
    }

    fun playNext() {
        if (_repeatMode.value == 2 && _currentSong.value != null) {
            playSong(_currentSong.value!!)
            return
        }
        val currentIdx = _playlist.value.indexOf(_currentSong.value)
        if (currentIdx != -1 && currentIdx < _playlist.value.size - 1) {
            playSong(_playlist.value[currentIdx + 1])
        } else if (_playlist.value.isNotEmpty()) {
            if (_repeatMode.value == 1 || _shuffleMode.value) {
                playSong(_playlist.value.first()) // wrap around
            } else {
                mediaPlayer?.pause()
                _isPlaying.value = false
                updateNotification()
            }
        }
    }

    fun playPrevious() {
        val currentIdx = _playlist.value.indexOf(_currentSong.value)
        if (currentIdx > 0) {
            playSong(_playlist.value[currentIdx - 1])
        } else if (_playlist.value.isNotEmpty()) {
            playSong(_playlist.value.last()) // wrap around
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _progressMs.value = positionMs
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (_isPlaying.value) {
                    _progressMs.value = mediaPlayer?.currentPosition?.toLong() ?: 0L
                }
                delay(100L)
            }
        }
    }

    private fun updateNotification() {
        val song = _currentSong.value ?: return
        
        val playPauseIcon = if (_isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (_isPlaying.value) "Pause" else "Play"

        val prevIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, NotificationReceiver::class.java).apply { action = "PREV" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playIntent = PendingIntent.getBroadcast(
            context, 2, Intent(context, NotificationReceiver::class.java).apply { action = "PLAY_PAUSE" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            context, 3, Intent(context, NotificationReceiver::class.java).apply { action = "NEXT" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var bitmap: android.graphics.Bitmap? = null
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, song.uri)
            val data = mmr.embeddedPicture
            if (data != null) {
                // Decode bounds first to shrink image and avoid TransactionTooLargeException
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                options.inSampleSize = maxOf(options.outWidth / 256, options.outHeight / 256, 1)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            }
            mmr.release()
        } catch (e: Exception) {}

        val builder = Notification.Builder(context, "music_channel")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(bitmap)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_media_previous), "Previous", prevIntent).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, playPauseIcon), playPauseTitle, playIntent).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_media_next), "Next", nextIntent).build())

        notificationManager.notify(1, builder.build())
    }

    fun release() {
        try { context.unregisterReceiver(audioNoisyReceiver) } catch (e: Exception) {}
        progressJob?.cancel()
        notificationManager.cancel(1)
        mediaSession.release()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
