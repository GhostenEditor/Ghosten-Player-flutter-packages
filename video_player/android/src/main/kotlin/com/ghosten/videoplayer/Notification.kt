package com.ghosten.videoplayer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

class Notification(private val context: Context, private val mediaSession: MediaSession) {
    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Now Playing"
            val descriptionText = "Now Playing"
            val channel =
                NotificationChannel(
                    context.getString(CHANNEL_ID),
                    name,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = descriptionText
                }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    class ActionBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    private fun createAction(ac: String, icon: Int, title: String): NotificationCompat.Action {
        val intent = Intent(context, ActionBroadcastReceiver::class.java).apply {
            action = Intent.ACTION_MEDIA_BUTTON
            putExtra("EXTRA_NOTIFICATION_ID", ac)
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 0, intent, PENDING_INTENT_FLAG_MUTABLE)
        return NotificationCompat.Action(icon, title, pendingIntent)
    }

    @SuppressLint("MissingPermission")
    fun showNotification(mediaMetadata: MediaMetadata) {
        with(NotificationManagerCompat.from(context)) {
            notify(
                NOTIFICATION_ID, NotificationCompat.Builder(
                    context, context.getString(
                        CHANNEL_ID
                    )
                )
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
                    .setOngoing(true)
                    .addAction(
                        createAction(
                            "Seek to previous item",
                            androidx.media3.session.R.drawable.media3_icon_previous,
                            "Seek to previous item"
                        )
                    )
                    .addAction(
                        createAction(
                            "pause",
                            androidx.media3.session.R.drawable.media3_icon_pause,
                            "Pause"
                        )
                    )
                    .addAction(
                        createAction(
                            "Seek to next item",
                            androidx.media3.session.R.drawable.media3_icon_next,
                            "Seek to next item"
                        )
                    )
                    .setStyle(
                        MediaStyleNotificationHelper.MediaStyle(mediaSession)
                            .setShowActionsInCompactView(0, 1, 2)
                    )
                    .setContentTitle(mediaMetadata.title)
                    .setContentText(mediaMetadata.artist)
                    .build()
            )
        }
    }

    fun release() {
        with(NotificationManagerCompat.from(context)) {
            cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 3423523
        private val CHANNEL_ID = R.string.default_player_channel_id
        private val PENDING_INTENT_FLAG_MUTABLE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }
}
