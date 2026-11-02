package com.ghosten.videoplayer

import android.content.Context
import android.graphics.Matrix
import android.view.View
import androidx.media3.common.Player
import io.flutter.plugin.common.MethodChannel


class MpvPlayer(
    context: Context, version: String, hwdec: Boolean,
    preferredSubtitleLanguage: String?,
    preferredAudioLanguage: String?,
    backgroundColor: Int?,
    subtitleStyle: List<Int>?,
) : MpvBasePlayer(context, version, hwdec, preferredSubtitleLanguage, preferredAudioLanguage, backgroundColor),
    GhostenPlayer {
    override val player: Player
        get() = this
    override val playlist: Array<Video>
        get() = arrayOf()
    override val playerView: View
        get() = surfaceView

    override val networkSpeed: Long
        get() = mNetworkSpeed

    override fun updateSource(video: Video) {
        setMediaItem(video.toMediaItem(), video.startPosition)
    }

    override fun setSource(video: Video?) {
        if (video != null) {
            setMediaItem(video.toMediaItem(), video.startPosition)
        } else {
            clearMediaItems()
        }
    }

    override fun setTransform(matrix: ArrayList<Double>) {
        surfaceView.animationMatrix = Matrix().apply {
            setValues(matrix.map { it.toFloat() }.toFloatArray())
        }
    }

    override fun setAspectRatio(aspectRatio: Float?) {
        MPVLib.setPropertyString("video-aspect-override", aspectRatio?.toString() ?: "-1")
    }

    override fun setResizeMode(mode: Int) {
    }

    override fun setTrack(trackType: String?, trackId: String?) {
        val property = when (trackType) {
            "video" -> "vid"
            "audio" -> "aid"
            "sub" -> "sid"
            else -> return
        }
        if (trackId == null) {
            MPVLib.setPropertyString(property, "no")
        } else {
            MPVLib.setPropertyString(property, trackId)
        }
    }

    override fun getVideoThumbnail(result: MethodChannel.Result, timeMs: Long) {
    }

    override fun setPlayerOption(name: String, value: Any) {
    }

    override fun setSubtitleStyle(style: List<Int>) {
    }
}
