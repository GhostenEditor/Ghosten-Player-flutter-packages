package com.ghosten.videoplayer

import android.content.Context
import android.graphics.Matrix
import android.net.TrafficStats
import android.os.Handler
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import io.flutter.plugin.common.MethodChannel
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType

class Media3Player(
    private val context: Context,
    private var extensionRendererMode: Int?,
    private var enableDecoderFallback: Boolean?,
    backgroundColor: Int?,
    private var preferredSubtitleLanguage: String?,
    private var preferredAudioLanguage: String?,
    subtitleStyle: List<Int>?,
) : GhostenPlayer {
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
//        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
    private var mPlaylist: Array<Video> = arrayOf()
    private val mplayerView: PlayerView = View.inflate(context, R.layout.player_view, null) as PlayerView
    private lateinit var handler: Handler
    private var mLastTotalRxBytes: Long? = null
    private var mNetworkSpeed: Long = 0
    private lateinit var mPlayer: ExoPlayer
    override val player: Player
        get() = mPlayer

    init {
        if (backgroundColor != null) {
            mplayerView.setShutterBackgroundColor(backgroundColor)
            mplayerView.setBackgroundColor(backgroundColor)
        }

        initPlayer()

        if (subtitleStyle?.size == 4) {
            setSubtitleStyle(subtitleStyle)
        }
        checkNetworkSpeed(1000)
    }

    private fun initPlayer() {
        mPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(enableDecoderFallback ?: false)
                    .setExtensionRendererMode(
                        extensionRendererMode
                            ?: DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    )
                    .forceEnableMediaCodecAsynchronousQueueing()
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    DefaultDataSource.Factory(context, httpDataSourceFactory)
                )
            )
            .setSeekParameters(SeekParameters(3000000, 3000000))
//            .build()
            .buildWithAssSupport(
                context, AssRenderType.CUES
            )
        player.playWhenReady = true
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguages(*listOfNotNull(preferredSubtitleLanguage).toTypedArray())
            .setPreferredAudioLanguages(*listOfNotNull(preferredAudioLanguage).toTypedArray())
//            .setMaxVideoSizeSd()
            .build()
        (player as ExoPlayer).addAnalyticsListener(EventLogger())
        player.prepare()
        mplayerView.player = player
        handler = Handler(player.applicationLooper)
    }

    private fun resetPlayer() {
        val index = player.currentMediaItemIndex
        val isPlaying = player.isPlaying
        mPlaylist[index].startPosition = player.currentPosition
        player.release()
        initPlayer()
        player.setMediaItem(mPlaylist[0].toMediaItem(), mPlaylist[index].startPosition)
        if (isPlaying) {
            player.prepare()
        }
    }

    private fun checkNetworkSpeed(delayMs: Long): Boolean {
        return handler.postDelayed(
            {
                val currentTotalRxBytes = TrafficStats.getTotalRxBytes()
                if (mLastTotalRxBytes != null) {
                    mNetworkSpeed = currentTotalRxBytes - mLastTotalRxBytes!!
                }
                mLastTotalRxBytes = currentTotalRxBytes
                checkNetworkSpeed(delayMs)
            },
            delayMs
        )
    }

//    override fun play() {
//        when (player.playbackState) {
//            Player.STATE_BUFFERING -> {
//                player.play()
//            }
//
//            Player.STATE_ENDED -> {
//                if (player.mediaItemCount > 0)
//                    player.seekTo(0, mPlaylist[0].startPosition)
//            }
//
//            Player.STATE_IDLE -> {
//                player.prepare()
//                player.play()
//            }
//
//            Player.STATE_READY -> {
//                player.play()
//            }
//        }
//    }


    override val playlist: Array<Video>
        get() = mPlaylist

    override val playerView: View
        get() = mplayerView

    override val networkSpeed: Long
        get() = mNetworkSpeed

    override fun updateSource(video: Video) {
        mPlaylist = arrayOf(video)
        player.setMediaItem(video.toMediaItem(), video.startPosition)
    }

    override fun setSource(video: Video?) {
        if (video != null) {
            mPlaylist = arrayOf(video)
            player.setMediaItem(video.toMediaItem(), video.startPosition)
        } else {
            mPlaylist = arrayOf()
            player.clearMediaItems()
        }

    }

    override fun setTransform(matrix: ArrayList<Double>) {
        val videoView = mplayerView.findViewById<PlayerView>(R.id.video_view)
        videoView.animationMatrix = Matrix().apply {
            setValues(matrix.map { it.toFloat() }.toFloatArray())
        }

    }

    override fun setAspectRatio(aspectRatio: Float?) {
        val contentFrame =
            mplayerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        contentFrame.setAspectRatio(
            aspectRatio ?: if (player.videoSize.height == 0) {
                1.778f
            } else {
                player.videoSize.width.toFloat() / player.videoSize.height.toFloat()
            }
        )

    }

    override fun setResizeMode(mode: Int) {
        mplayerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame).resizeMode = mode
    }

    override fun setTrack(trackType: String?, trackId: String?) {
        val type = when (trackType) {
            "video" -> C.TRACK_TYPE_VIDEO
            "audio" -> C.TRACK_TYPE_AUDIO
            "sub" -> C.TRACK_TYPE_TEXT
            else -> return
        }
        for (trackGroup in player.currentTracks.groups) {
            if (trackGroup.type == type) {
                if (trackId == null) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(trackGroup.type, true)
                        .build()
                    return
                }
                for (i in 0 until trackGroup.length) {
                    val isSupported = trackGroup.isTrackSupported(i)
                    val trackFormat = trackGroup.getTrackFormat(i)
                    if (isSupported && trackFormat.id == trackId) {
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(trackGroup.mediaTrackGroup.type, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        trackGroup.mediaTrackGroup,
                                        i
                                    )
                                )
                                .build()
                        break
                    }
                }

            }
        }
    }

    override fun getVideoThumbnail(result: MethodChannel.Result, timeMs: Long) {
//        thumbnailThread.getUrl(mPlaylist[player.currentMediaItemIndex].url, timeMs) {
//            result.success(it)
//        }
    }

    override fun setPlayerOption(name: String, value: Any) {
        when (name) {
            "extensionRendererMode" -> {
                extensionRendererMode = value as Int
                resetPlayer()
            }

            "enableDecoderFallback" -> {
                enableDecoderFallback = value as Boolean
                resetPlayer()
            }
        }
    }

    override fun setSubtitleStyle(style: List<Int>) {
        if (style.size != 4) return
        val subtitle = mplayerView.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles)
        subtitle.setStyle(
            CaptionStyleCompat(
                style[0],
                style[1],
                style[2],
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                style[3],
                null
            )
        )
    }

    companion object {
//        private const val USER_AGENT =
//            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.83 Safari/537.36"
    }
}
