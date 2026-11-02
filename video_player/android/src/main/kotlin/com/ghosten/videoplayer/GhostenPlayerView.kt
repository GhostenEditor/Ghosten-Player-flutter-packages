package com.ghosten.videoplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.View
import androidx.media3.common.*
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.upstream.Loader
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

class GhostenPlayerView internal constructor(
    context: Context,
    private val activity: Activity,
    private val mChannel: MethodChannel,
    private val autoPip: Boolean,
    private var player: GhostenPlayer,
) : PlatformView, Player.Listener {
    class Builder internal constructor(
        private val context: Context, private val activity: Activity, private val mChannel: MethodChannel
    ) {
        private var autoPip: Boolean = false
        private var backgroundColor: Int? = null
        private var preferredSubtitleLanguage: String? = null
        private var preferredAudioLanguage: String? = null
        private var subtitleStyle: List<Int>? = null
        fun media3Builder(): Media3Builder {
            return Media3Builder(
                context,
                activity,
                mChannel,
                autoPip,
                backgroundColor,
                preferredSubtitleLanguage,
                preferredAudioLanguage,
                subtitleStyle
            )
        }

        fun mpvBuilder(): MpvBuilder {
            return MpvBuilder(
                context, activity, mChannel, autoPip, backgroundColor, preferredSubtitleLanguage,
                preferredAudioLanguage, subtitleStyle
            )
        }

        fun setAutoPip(autoPip: Boolean): Builder {
            this.autoPip = autoPip
            return this
        }

        fun setBackgroundColor(backgroundColor: Int?): Builder {
            this.backgroundColor = backgroundColor
            return this
        }

        fun setPreferredSubtitleLanguage(language: String?): Builder {
            this.preferredSubtitleLanguage = language
            return this
        }

        fun setPreferredAudioLanguage(language: String?): Builder {
            this.preferredAudioLanguage = language
            return this
        }

        fun setSubtitleStyle(subtitleStyle: List<Int>?): Builder {
            this.subtitleStyle = subtitleStyle
            return this
        }
    }

    class Media3Builder internal constructor(
        private val context: Context,
        private val activity: Activity,
        private val mChannel: MethodChannel,
        private var autoPip: Boolean = false,
        private val backgroundColor: Int?,
        private var preferredSubtitleLanguage: String?,
        private var preferredAudioLanguage: String?,
        private val subtitleStyle: List<Int>?,
    ) {
        private var extensionRendererMode: Int? = null
        private var enableDecoderFallback: Boolean? = null

        fun setExtensionRendererMode(extensionRendererMode: Int?): Media3Builder {
            this.extensionRendererMode = extensionRendererMode
            return this
        }

        fun setEnableDecoderFallback(enableDecoderFallback: Boolean?): Media3Builder {
            this.enableDecoderFallback = enableDecoderFallback
            return this
        }

        fun build(): GhostenPlayerView {
//            val thumbnailThread = ThumbnailThread(context)
            return GhostenPlayerView(
                context, activity, mChannel, autoPip, Media3Player(
                    context,
                    extensionRendererMode,
                    enableDecoderFallback,
                    backgroundColor,
                    preferredSubtitleLanguage,
                    preferredAudioLanguage,
                    subtitleStyle,
                )
            )
        }
    }

    class MpvBuilder internal constructor(
        private val context: Context,
        private val activity: Activity,
        private val mChannel: MethodChannel,
        private var autoPip: Boolean = false,
        private val backgroundColor: Int?,
        private var preferredSubtitleLanguage: String?,
        private var preferredAudioLanguage: String?,
        private val subtitleStyle: List<Int>?,
    ) {
        private var version: String? = null
        private var hwdec: Boolean = false

        fun setVersion(version: String): MpvBuilder {
            this.version = version
            return this
        }

        fun setHwdec(hwdec: Boolean): MpvBuilder {
            this.hwdec = hwdec
            return this
        }

        fun build(): GhostenPlayerView {
            assert(version != null)
            return GhostenPlayerView(
                context, activity, mChannel, autoPip, MpvPlayer(
                    context,
                    version!!,
                    hwdec,
                    preferredSubtitleLanguage,
                    preferredAudioLanguage,
                    backgroundColor,
                    subtitleStyle
                )
            )
        }
    }

    private val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
    private val handler: Handler = Handler(player.player.applicationLooper)
    private val mediaSession: MediaSession = MediaSession.Builder(context, player.player).build()
    private val notification: Notification = Notification(context, mediaSession)


    init {
//        thumbnailThread.start()
        player.player.addListener(this)
        checkPlaybackPosition(1000)
        mChannel.setMethodCallHandler(player)
    }

    private fun checkPlaybackPosition(delayMs: Long): Boolean {
        return handler.postDelayed(
            {
                if (player.player.isPlaying) {
                    mChannel.invokeMethod("position", player.player.currentPosition)
                    mChannel.invokeMethod("bufferingUpdate", player.player.bufferedPosition)
                }
                mChannel.invokeMethod("networkSpeed", player.networkSpeed)
                checkPlaybackPosition(delayMs)
            }, delayMs
        )
    }

    override fun getView(): View {
        return player.playerView
    }

    override fun dispose() {
        mChannel.setMethodCallHandler(null)
        notification.release()
        mediaSession.release()
        player.player.release()
        setPictureInPictureParams()
        handler.removeCallbacksAndMessages(null)
//        thumbnailThread.cancel()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        setPictureInPictureParams()
        mChannel.invokeMethod("isPlaying", isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        mChannel.invokeMethod("mediaChanged", HashMap<String, Any?>().apply {
            this["index"] = player.player.currentMediaItemIndex
            this["position"] = player.player.currentPosition
        })
//        thumbnailThread.clear()
        super.onMediaItemTransition(mediaItem, reason)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        notification.showNotification(mediaMetadata)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION, Player.DISCONTINUITY_REASON_SEEK -> {
                if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                    mChannel.invokeMethod("beforeMediaChange", HashMap<String, Any?>().apply {
                        this["index"] = oldPosition.mediaItemIndex
                        this["position"] = oldPosition.positionMs
                    })
                    player.playlist[oldPosition.mediaItemIndex].startPosition = oldPosition.positionMs
                    player.player.trackSelectionParameters =
                        player.player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                            .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, false)
                            .setTrackTypeDisabled(C.TRACK_TYPE_METADATA, false).build()
                    if (player.playlist[newPosition.mediaItemIndex].startPosition < player.player.duration * 0.95) {
                        player.player.seekTo(player.playlist[newPosition.mediaItemIndex].startPosition)
                    }
                }
            }

            else -> {
            }
        }
        mChannel.invokeMethod("position", newPosition.positionMs)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        mChannel.invokeMethod("playbackState", playbackState)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        mChannel.invokeMethod(
            "duration", if (player.player.duration == C.TIME_UNSET) 0 else player.player.duration
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        when (error) {
            is ExoPlaybackException -> {
                when (val cause = error.cause) {
                    is HttpDataSource.InvalidResponseCodeException -> {
                        if (cause.responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                            val host = cause.dataSpec.uri.host
                            if (host != null && (host.endsWith("aliyuncs.com") || host.endsWith("aliyundrive.net"))) {
                                player.player.play()
                            } else {
                                mChannel.invokeMethod(
                                    "fatalError", "${cause.responseCode} ${cause.responseBody.decodeToString()}"
                                )
                            }
                        } else {
                            mChannel.invokeMethod(
                                "fatalError", "${cause.responseCode} ${cause.responseBody.decodeToString()}"
                            )
                        }
                    }

                    is HttpDataSource.HttpDataSourceException -> {
                        when (val httpCause = cause.cause) {
                            is SocketTimeoutException -> {
                                mChannel.invokeMethod("fatalError", "Connection Timeout")
                            }

                            else -> {
                                mChannel.invokeMethod("fatalError", httpCause?.message)
                            }
                        }
                    }

                    is FileDataSource.FileDataSourceException -> {
                        when (cause.cause) {
                            is FileNotFoundException -> {
                                mChannel.invokeMethod("fatalError", "This File is Not Found")
                            }

                            else -> {
                                mChannel.invokeMethod("fatalError", cause.message)
                            }
                        }
                    }


                    is MediaCodecDecoderException, is DecoderInitializationException -> {
                        player.player.trackSelectionParameters =
                            player.player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(
                                (player.player as ExoPlayer).getRenderer(error.rendererIndex).trackType, true
                            ).build()
                        player.player.prepare()
                        mChannel.invokeMethod("error", cause.message)
                        mChannel.invokeMethod("fatalError", cause.message)
                    }

                    is UnrecognizedInputFormatException -> {
                        mChannel.invokeMethod(
                            "fatalError", "None of the available extractors could read the stream."
                        )
                    }

                    is BehindLiveWindowException -> {
                        player.player.prepare()
                        player.player.play()
                        mChannel.invokeMethod("error", cause.message)
                        mChannel.invokeMethod("fatalError", cause.message)
                    }

                    is Loader.UnexpectedLoaderException -> {
                        when (val loaderCause = cause.cause) {
                            is SecurityException -> {
                                mChannel.invokeMethod("fatalError", loaderCause.message)
                            }

                            is IllegalArgumentException -> {
                                mChannel.invokeMethod("fatalError", cause.message)
                            }

                            is IllegalStateException -> {
                                if (player.player.currentMediaItemIndex < player.player.mediaItemCount - 1) {
                                    next(player.player.currentMediaItemIndex + 1)
                                    player.player.play()
                                }
                                mChannel.invokeMethod("error", loaderCause.message)
                            }

                            else -> {
                                mChannel.invokeMethod("fatalError", loaderCause?.message)
                            }
                        }

                    }

                    else -> {
                        mChannel.invokeMethod("fatalError", cause?.message)
                    }
                }
            }

            else -> {
                mChannel.invokeMethod("fatalError", error.cause?.message)
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        val formats = mutableListOf<HashMap<String, Any?>>()
        for (trackGroup in tracks.groups) {
            for (i in 0 until trackGroup.length) {
                val isSupported = trackGroup.isTrackSupported(i)
                if (!isSupported) continue
                val trackFormat = trackGroup.getTrackFormat(i)
                val isSelected = trackGroup.isTrackSelected(i)
                formats.add(trackFormat.toHashMap(isSelected, trackNameProvider))
            }
        }
        mChannel.invokeMethod("tracksChanged", formats)
    }

    private fun setMediaSkipEnd() {
//        val index = player.currentMediaItemIndex
//        val item = player.playlist[index]
//        val endPosition = item.endPosition
//        if (player.duration != C.TIME_UNSET && endPosition > 0 && player.duration > endPosition) {
//            if (item.skipEnd != null && !item.skipEnd!!.isCanceled) {
//                item.skipEnd!!.cancel()
//            }
//            item.skipEnd = (player as ExoPlayer).createMessage { _: Int, payload: Any? ->
//                next(payload as Int)
//            }.setLooper(Looper.getMainLooper()).setPosition(index, player.duration - endPosition).setPayload(index + 1)
//                .setDeleteAfterDelivery(true).send()
//            if (item.skipEndTip != null && !item.skipEndTip!!.isCanceled) {
//                item.skipEndTip!!.cancel()
//            }
//            item.skipEndTip = (player as ExoPlayer).createMessage { _: Int, _: Any? ->
//                mChannel.invokeMethod("willSkip", null)
//            }.setLooper(Looper.getMainLooper()).setPosition(index, player.duration - endPosition - 15000)
//                .setPayload(index + 1).setDeleteAfterDelivery(true).send()
//        }
    }

    private fun setPictureInPictureParams() {
        if (!autoPip) return
        val params = getPictureInPictureParams()
        if (params != null) activity.setPictureInPictureParams(params)
    }

    fun getPictureInPictureParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PictureInPictureParams.Builder().setAutoEnterEnabled(player.player.isPlaying).build()
            } else {
                PictureInPictureParams.Builder().build()
            }
        }
        return null
    }

    fun next(index: Int) {
        if (index >= player.player.mediaItemCount) {
            player.player.seekTo(player.player.duration)
            return
        }
        if (index < 0) {
            return
        }
        player.player.seekTo(index, player.playlist[index].startPosition)
        if (player.player.playbackState == Player.STATE_IDLE) {
            player.player.prepare()
            player.player.play()
        }
    }

}
