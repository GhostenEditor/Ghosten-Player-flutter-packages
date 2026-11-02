package com.ghosten.videoplayer

import android.view.View
import androidx.media3.common.Player
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

interface GhostenPlayer : MethodChannel.MethodCallHandler {
    val player: Player
    val playlist: Array<Video>
    val playerView: View
    val networkSpeed: Long

    //    fun play()
//    fun pause()
//    fun seekTo(positionMs: Long)
//    fun seekTo(mediaItemIndex: Int, positionMs: Long)
    fun updateSource(video: Video)
    fun setSource(video: Video?)
    fun setTransform(matrix: ArrayList<Double>)
    fun setAspectRatio(aspectRatio: Float?)
    fun setResizeMode(mode: Int)
    fun setTrack(trackType: String?, trackId: String?)
    fun getVideoThumbnail(result: MethodChannel.Result, timeMs: Long)
    fun setPlayerOption(name: String, value: Any)
    fun setSubtitleStyle(style: List<Int>)

    @Suppress("UNCHECKED_CAST")
    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {
            "play" -> player.play()
            "pause" -> player.pause()
            "next" -> player.seekTo(call.arguments as Int, 0)
            "seekTo" -> player.seekTo((call.arguments as Int).toLong())
            "updateSource" -> updateSource((call.argument("source") as? HashMap<String, Any>)!!.toVideo())
            "setSource" -> setSource((call.arguments as? Map<String, Any>?)?.toVideo())
            "setTransform" -> setTransform(call.argument("matrix")!!)
            "setAspectRatio" -> setAspectRatio((call.arguments as Double?)?.toFloat())
            "setResizeMode" -> setResizeMode(call.arguments as Int)
            "setTrack" -> setTrack(
                call.argument("type"),
                call.argument("id")
            )

            "getVideoThumbnail" -> return getVideoThumbnail(
                result,
                call.argument("position")!!
            )

            "setPlaybackSpeed" -> player.setPlaybackSpeed((call.arguments as Double).toFloat())
            "setPlayerOption" -> setPlayerOption(
                call.argument("name")!!,
                call.argument("value")!!
            )

            "setSubtitleStyle" -> setSubtitleStyle(call.argument("style")!!)
            else -> return result.notImplemented()
        }
        result.success(null)
    }
}
