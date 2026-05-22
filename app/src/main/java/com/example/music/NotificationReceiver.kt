package com.example.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = MusicPlayerController.getInstance(context)
        when (intent.action) {
            "PLAY_PAUSE" -> controller.togglePlayPause()
            "NEXT" -> controller.playNext()
            "PREV" -> controller.playPrevious()
        }
    }
}
