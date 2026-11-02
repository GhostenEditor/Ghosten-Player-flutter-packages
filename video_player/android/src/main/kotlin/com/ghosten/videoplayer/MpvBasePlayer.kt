package com.ghosten.videoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.*
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import com.ghosten.videoplayer.MPVLib.mpvFormat.*
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException

@SuppressLint("ViewConstructor")
open class MpvBasePlayer(
    private val context: Context, version: String, hwdec: Boolean,
    private var preferredSubtitleLanguage: String?,
    private var preferredAudioLanguage: String?,
    private val backgroundColor: Int?,
) : BasePlayer(), MPVLib.EventObserver, SurfaceHolder.Callback {
    val surfaceView: SurfaceView = SurfaceView(context)
    private val trackSelector: TrackSelector = DefaultTrackSelector(context)
    private var playlistMetadata: MediaMetadata = MediaMetadata.EMPTY
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
    private var tracks: Tracks = Tracks.EMPTY
    private var timeline: Timeline = Timeline.EMPTY
    private var coreIdle: Boolean = false
    private var pause: Boolean = false
    private var pausedForCache: Boolean = false
    private var seeking: Boolean = false
    private var videoAspectRatio: Double = 1.0
    private var duration: Long = 0
    private var currentPosition: Long = 0
    private var bufferedPosition: Long = 0
    var mNetworkSpeed: Long = 0
    private val handler = Handler(applicationLooper)
    private val listeners: ListenerSet<Player.Listener> = ListenerSet(applicationLooper)
    var videoFormat: Format? = null
    var audioFormat: Format? = null
    private var shuffleModeEnabled = false
    private var repeatMode: @Player.RepeatMode Int = REPEAT_MODE_OFF
    private var permanentAvailableCommands: Player.Commands = Player.Commands.Builder().addAll(
        COMMAND_PLAY_PAUSE,
        COMMAND_PREPARE,
        COMMAND_STOP,
        COMMAND_SET_SPEED_AND_PITCH,
        COMMAND_SET_SHUFFLE_MODE,
        COMMAND_SET_REPEAT_MODE,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_METADATA,
        COMMAND_SET_PLAYLIST_METADATA,
        COMMAND_SET_MEDIA_ITEM,
        COMMAND_CHANGE_MEDIA_ITEMS,
        COMMAND_GET_TRACKS,
        COMMAND_GET_AUDIO_ATTRIBUTES,
        COMMAND_SET_AUDIO_ATTRIBUTES,
        COMMAND_GET_VOLUME,
        COMMAND_SET_VOLUME,
        COMMAND_SET_VIDEO_SURFACE,
        COMMAND_GET_TEXT,
        COMMAND_RELEASE
    ).addIf(COMMAND_SET_TRACK_SELECTION_PARAMETERS, trackSelector.isSetParametersSupported)
//        .addIf(Player.COMMAND_GET_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
//        .addIf(Player.COMMAND_SET_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
//        .addIf(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS, builder.deviceVolumeControlEnabled)
//        .addIf(Player.COMMAND_ADJUST_DEVICE_VOLUME, builder.deviceVolumeControlEnabled)
//        .addIf(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS, builder.deviceVolumeControlEnabled)
        .build()

    private var availableCommands: Player.Commands =
        Player.Commands.Builder().addAll(permanentAvailableCommands).add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_DEFAULT_POSITION).add(COMMAND_SEEK_TO_MEDIA_ITEM).build()
    private var mediaItem: MediaItem? = null

    init {
        trackSelector.parameters = trackSelector.parameters
            .buildUpon()
            .setPreferredTextLanguages(*listOfNotNull(preferredSubtitleLanguage).toTypedArray())
            .setPreferredAudioLanguages(*listOfNotNull(preferredAudioLanguage).toTypedArray())
            .build()
        loadMpvLibs(version)

        MPVLib.create(context)

        initOptions(version, hwdec)

        MPVLib.init()

        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "yes")

        observeProperties()
        addObserver(this)
        setVideoSurfaceView(surfaceView)
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadMpvLibs(version: String) {
        val dir = File(context.filesDir.path.plus("/mpv/$version"))
        val manifest = File(dir, "manifest.json")
        if (!manifest.exists()) {
            throw FileNotFoundException("MPV is not installed")
        }
        val libs = manifest.bufferedReader().use { JSONArray(it.readText()) }
        for (i in 0..<libs.length()) {
            val lib = libs.getString(i)
            System.load(File(dir, "$lib.so").path)
        }
    }

    private fun initOptions(version: String, hwdec: Boolean) {
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", context.filesDir.path.plus("/mpv/$version"))
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir")) MPVLib.setOptionString(opt, context.cacheDir.path)

        MPVLib.setOptionString("profile", "fast")

        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("osd-on-seek", "no")
        val preferredVideoLanguages = trackSelector.parameters.preferredVideoLanguages
        if (preferredVideoLanguages.isNotEmpty()) {
            MPVLib.setOptionString("vlang", preferredVideoLanguages.joinToString(","))
        }
        val preferredAudioLanguages = trackSelector.parameters.preferredAudioLanguages
        if (preferredAudioLanguages.isNotEmpty()) {
            MPVLib.setOptionString("alang", preferredAudioLanguages.joinToString(","))
        }
        val preferredTextLanguages = trackSelector.parameters.preferredTextLanguages
        if (preferredTextLanguages.isNotEmpty()) {
            MPVLib.setOptionString("slang", preferredTextLanguages.joinToString(","))
        }
        MPVLib.setOptionString("resume-playback", "no")

        MPVLib.setOptionString("vo", "gpu")

        MPVLib.setOptionString("gpu-context", "android")
        if (backgroundColor != null)
            MPVLib.setOptionString("background-color", "#${backgroundColor.toHexString()}")
        MPVLib.setOptionString("opengl-es", "yes")
        if (hwdec) {
            MPVLib.setOptionString("hwdec", "auto")
            MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        } else {
            MPVLib.setOptionString("hwdec", "no")
        }
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("input-default-bindings", "no")
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("sub-back-color", "#000000")

        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")
    }

    private fun observeProperties() {
        MPVLib.observeProperty("time-pos/full", MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration/full", MPV_FORMAT_INT64)
        MPVLib.observeProperty("playlist-playing-pos", MPV_FORMAT_INT64)
        MPVLib.observeProperty("pause", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("core-idle", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("seeking", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("speed", MPV_FORMAT_STRING)
        MPVLib.observeProperty("track-list", MPV_FORMAT_NONE)
        MPVLib.observeProperty("video-params/aspect", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("video-params/rotate", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("playlist-pos", MPV_FORMAT_INT64)
        MPVLib.observeProperty("playlist-count", MPV_FORMAT_INT64)
        MPVLib.observeProperty("current-tracks/video/image", MPV_FORMAT_NONE)
        MPVLib.observeProperty("media-title", MPV_FORMAT_STRING)
        MPVLib.observeProperty("metadata", MPV_FORMAT_NONE)
        MPVLib.observeProperty("loop-playlist", MPV_FORMAT_NONE)
        MPVLib.observeProperty("loop-file", MPV_FORMAT_NONE)
        MPVLib.observeProperty("shuffle", MPV_FORMAT_FLAG)
        MPVLib.observeProperty("hwdec-current", MPV_FORMAT_NONE)
        MPVLib.observeProperty("cache-speed", MPV_FORMAT_INT64)
        MPVLib.observeProperty("estimated-vf-fps", MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("end-file/error", MPV_FORMAT_STRING)

    }

    private fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }

    private fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    private fun loadTracks() {
        val count = MPVLib.getPropertyInt("track-list/count")!!

        val trackTypes = arrayOf("audio", "video", "sub")
        val videoFormats = mutableListOf<Format>()
        val audioFormats = mutableListOf<Format>()
        val textFormats = mutableListOf<Format>()
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!trackTypes.contains(type)) {
                Log.w(TAG, "Got unknown track type: $type")
                continue
            }
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val srcId = MPVLib.getPropertyString("track-list/$i/src-id")
            val title = MPVLib.getPropertyString("track-list/$i/title")
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val image = MPVLib.getPropertyBoolean("track-list/$i/image")
            val albumart = MPVLib.getPropertyBoolean("track-list/$i/albumart")
            val default = MPVLib.getPropertyBoolean("track-list/$i/default")
            val forced = MPVLib.getPropertyBoolean("track-list/$i/forced")
            val dependent = MPVLib.getPropertyBoolean("track-list/$i/dependent")
            val visualImpaired = MPVLib.getPropertyBoolean("track-list/$i/visual-impaired")
            val hearingImpaired = MPVLib.getPropertyBoolean("track-list/$i/hearing-impaired")
            val hlsBitrate = MPVLib.getPropertyInt("track-list/$i/hls-bitrate")
            val programId = MPVLib.getPropertyInt("track-list/$i/program-id")
            val selected = MPVLib.getPropertyBoolean("track-list/$i/selected")
            val mainSelection = MPVLib.getPropertyInt("track-list/$i/main-selection")
            val external = MPVLib.getPropertyBoolean("track-list/$i/external")
            val externalFilename = MPVLib.getPropertyString("track-list/$i/external-filename")
            val codec = MPVLib.getPropertyString("track-list/$i/codec")
            val codecDesc = MPVLib.getPropertyString("track-list/$i/codec-desc")
            val codecProfile = MPVLib.getPropertyString("track-list/$i/codec-profile")
            val ffIndex = MPVLib.getPropertyInt("track-list/$i/ff-index")
            val decoder = MPVLib.getPropertyString("track-list/$i/decoder")
            val decoderDesc = MPVLib.getPropertyString("track-list/$i/decoder-desc")
            val demuxW = MPVLib.getPropertyInt("track-list/$i/demux-w")
            val demuxH = MPVLib.getPropertyInt("track-list/$i/demux-h")
            val demuxCropX = MPVLib.getPropertyInt("track-list/$i/demux-crop-x")
            val demuxCropY = MPVLib.getPropertyInt("track-list/$i/demux-crop-y")
            val demuxCropW = MPVLib.getPropertyInt("track-list/$i/demux-crop-w")
            val demuxCropH = MPVLib.getPropertyInt("track-list/$i/demux-crop-h")
            val demuxChannelCount = MPVLib.getPropertyString("track-list/$i/demux-channel-count")
            val demuxChannels = MPVLib.getPropertyString("track-list/$i/demux-channels")
            val demuxSamplerate = MPVLib.getPropertyInt("track-list/$i/demux-samplerate")
            val demuxFps = MPVLib.getPropertyDouble("track-list/$i/demux-fps")
            val demuxBitrate = MPVLib.getPropertyInt("track-list/$i/demux-bitrate")
            val demuxRotation = MPVLib.getPropertyInt("track-list/$i/demux-rotation")
            val demuxPar = MPVLib.getPropertyDouble("track-list/$i/demux-par")
            val formatName = MPVLib.getPropertyString("track-list/$i/format-name")
            val audioChannels = MPVLib.getPropertyInt("track-list/$i/audio-channels")
            val replaygainTrackPeak = MPVLib.getPropertyDouble("track-list/$i/replaygain-track-peak")
            val replaygainTrackGain = MPVLib.getPropertyDouble("track-list/$i/replaygain-track-gain")
            val replaygainAlbumPeak = MPVLib.getPropertyDouble("track-list/$i/replaygain-album-peak")
            val replaygainAlbumGain = MPVLib.getPropertyDouble("track-list/$i/replaygain-album-gain")
            val dolbyVisionProfile = MPVLib.getPropertyInt("track-list/$i/dolby-vision-profile")
            val dolbyVisionLevel = MPVLib.getPropertyInt("track-list/$i/dolby-vision-level")

            val format = Format.Builder()
                .setId(id)
                .setLanguage(lang)
                .setLabel(title)
                .setSampleMimeType(
                    when (type) {
                        "audio" -> MimeTypes.AUDIO_UNKNOWN
                        "video" -> MimeTypes.VIDEO_UNKNOWN
                        "sub" -> MimeTypes.TEXT_UNKNOWN
                        else -> null
                    }
                )
                .setFrameRate(demuxFps?.toFloat() ?: Format.NO_VALUE.toFloat())
                .setWidth(demuxW ?: Format.NO_VALUE)
                .setHeight(demuxH ?: Format.NO_VALUE)
                .setSelectionFlags(
                    if (forced) {
                        C.SELECTION_FLAG_FORCED
                    } else if (selected) {
                        C.SELECTION_FLAG_AUTOSELECT
                    } else {
                        C.SELECTION_FLAG_DEFAULT
                    }
                )
                .setAverageBitrate(demuxSamplerate ?: Format.NO_VALUE)
                .setSampleRate(demuxBitrate ?: Format.NO_VALUE)
                .build()
            when (type) {
                "audio" -> audioFormats.add(format)
                "video" -> videoFormats.add(format)
                "sub" -> textFormats.add(format)
            }
        }
        val groups = (videoFormats + audioFormats + textFormats)
            .map {
                Tracks.Group(
                    TrackGroup(it),
                    true,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(it.selectionFlags == C.SELECTION_FLAG_FORCED || it.selectionFlags == C.SELECTION_FLAG_AUTOSELECT)
                )

            }
        tracks = Tracks(groups)
        handler.post {
            listeners.sendEvent(EVENT_TRACKS_CHANGED) {
                it.onTracksChanged(tracks)
            }
        }
    }

    private fun updateStatus() {
        Log.d("updateStatus", "$coreIdle,$pause,$pausedForCache,$seeking")
        handler.post {
            listeners.queueEvent(EVENT_PLAYBACK_STATE_CHANGED) {
                it.onPlaybackStateChanged(playbackState)
            }
            listeners.flushEvents()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setVideoSurfaceHolder(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        clearVideoSurfaceHolder(holder)
    }

    override fun eventProperty(property: String) {
        when (property) {
            "track-list" -> loadTracks()
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "duration/full" -> {
                duration = value * 1000
                if (mediaItem != null) {
                    timeline = SinglePeriodTimeline(
                        duration, true, false, false, null, mediaItem!!
                    )
                    handler.post {
                        listeners.sendEvent(EVENT_TIMELINE_CHANGED) {
                            it.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                        }
                    }
                }
            }

            "time-pos/full" -> {
                currentPosition = value * 1000
            }

            "cache-speed" -> {
                mNetworkSpeed = value
            }

            "playlist-playing-pos" -> {
                handler.post {
                    listeners.queueEvent(EVENT_MEDIA_ITEM_TRANSITION) {
                        it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_SEEK)
                    }
                    listeners.flushEvents()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "paused-for-cache" -> {
                pausedForCache = value
                updateStatus()
            }

            "pause" -> {
                pause = value
                handler.post {
                    listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) {
                        it.onIsPlayingChanged(!value)
                    }
                }
                updateStatus()
            }

            "core-idle" -> {
                coreIdle = value
                updateStatus()
            }

            "seeking" -> {
                seeking = value
                updateStatus()
            }

            "shuffle" -> shuffleModeEnabled = value
        }
    }

    override fun eventProperty(property: String, value: String) {
        Log.d("eventProperty", "$property: $value")
    }

    override fun eventProperty(property: String, value: Double) {
        Log.d("eventProperty", "$property: $value")
        when (property) {
            "video-params/aspect" -> {
                videoAspectRatio = value
                Log.d(TAG, "video-params/aspect $value")
            }

            "estimated-vf-fps" -> {
                Log.d(TAG, "estimated-vf-fps $value")
            }
        }
    }

    override fun event(eventId: Int) {
        if (eventId == MPVLib.mpvEventId.MPV_EVENT_VIDEO_RECONFIG) {
            surfaceView.setBackgroundColor(Color.TRANSPARENT)
        }
        Log.d("event", "eventId: $eventId")
    }

    override fun getApplicationLooper(): Looper {
        return Looper.getMainLooper()
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)

    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        setMediaItems(mediaItems, 0, 0)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        this.mediaItem = mediaItems.first()
        val mediaItem = this.mediaItem!!
        MPVLib.command(
            arrayOf(
                "loadfile",
                mediaItem.localConfiguration?.uri.toString(),
                "replace",
                "0",
                "start=${startPositionMs / 1000}",
            )
        )
        mediaItem.localConfiguration?.subtitleConfigurations?.forEach { subtitle ->
            MPVLib.command(
                arrayOf(
                    "sub-add", subtitle.uri.toString(), if (subtitle.selectionFlags == C.SELECTION_FLAG_AUTOSELECT) {
                        "select"
                    } else {
                        ""
                    }, subtitle.label
                )
            )
        }

        timeline = SinglePeriodTimeline(
            duration, true, false, false, null, mediaItem
        )

        handler.post {
            listeners.queueEvent(EVENT_MEDIA_METADATA_CHANGED) {
                it.onMediaMetadataChanged(mediaItem.mediaMetadata)
            }
            listeners.queueEvent(EVENT_TIMELINE_CHANGED) {
                it.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            }
            listeners.flushEvents()
        }
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        mediaItem = null
    }

    override fun getAvailableCommands(): Player.Commands {
        return availableCommands
    }

    override fun prepare() {
    }

    override fun getPlaybackState(): Int {
        return if (pausedForCache || seeking) {
            STATE_BUFFERING
        } else if (coreIdle) {
            STATE_IDLE
        } else {
            STATE_READY
        }
    }

    override fun getPlaybackSuppressionReason(): Int {
        return PLAYBACK_SUPPRESSION_REASON_NONE
    }

//    override fun isPlaying(): Boolean {
//        return !(pausedForCache || seeking) && !pause && !coreIdle
//    }

    override fun getPlayerError(): PlaybackException? {
        return null
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        MPVLib.setOptionString(
            "pause", if (playWhenReady) {
                "no"
            } else {
                "yes"
            }
        )
    }

    override fun getPlayWhenReady(): Boolean {
        return true
    }

    override fun setRepeatMode(repeatMode: Int) {
        when (repeatMode) {
            REPEAT_MODE_ALL -> {
                MPVLib.setOptionString("loop-playlist", "inf")
            }

            REPEAT_MODE_OFF -> {
                MPVLib.setOptionString("loop-playlist", "no")
                MPVLib.setOptionString("loop-file", "no")
            }

            REPEAT_MODE_ONE -> {
                MPVLib.setOptionString("loop-file", "inf")
            }
        }
    }

    override fun getRepeatMode(): Int {
        return repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        MPVLib.setOptionString(
            "shuffle", if (shuffleModeEnabled) {
                "no"
            } else {
                "yes"
            }
        )
    }

    override fun getShuffleModeEnabled(): Boolean {
        return shuffleModeEnabled
    }

    override fun isLoading(): Boolean {
        return pausedForCache || seeking
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekCommand: Int, isRepeatingCurrentItem: Boolean) {
        if (mediaItemIndex == currentMediaItemIndex) {
            MPVLib.command(arrayOf("seek", (positionMs / 1000).toString(), "absolute"))
        } else {
            MPVLib.command(arrayOf("playlist-play-index", mediaItemIndex.toString()))
        }
    }

    override fun getSeekBackIncrement(): Long {
        return C.DEFAULT_SEEK_BACK_INCREMENT_MS
    }

    override fun getSeekForwardIncrement(): Long {
        return C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (this.playbackParameters == playbackParameters) {
            return
        }
        this.playbackParameters = playbackParameters
        MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
        listeners.sendEvent(EVENT_PLAYBACK_PARAMETERS_CHANGED) { listeners ->
            listeners.onPlaybackParametersChanged(playbackParameters)
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return playbackParameters
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun release() {
        trackSelector.release()
        listeners.release()
        removeObserver(this)
        clearVideoSurfaceView(surfaceView)
        MPVLib.destroy()
    }

    override fun getCurrentTracks(): Tracks {
        return tracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return trackSelector.parameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        if (!trackSelector.isSetParametersSupported) {
            return
        }
        val publicParametersBeforeUpdate = trackSelectionParameters
        trackSelector.parameters = parameters
        if (publicParametersBeforeUpdate != parameters) {
            listeners.sendEvent(EVENT_TRACK_SELECTION_PARAMETERS_CHANGED) { listener ->
                listener.onTrackSelectionParametersChanged(parameters)
            }
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        return currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return mediaMetadata
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        if (mediaMetadata == this.playlistMetadata) {
            return
        }
        playlistMetadata = mediaMetadata
        listeners.sendEvent(EVENT_PLAYLIST_METADATA_CHANGED) { listener ->
            listener.onPlaylistMetadataChanged(
                mediaMetadata
            )
        }
    }

    override fun getCurrentTimeline(): Timeline {
        return timeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return 0
    }

    override fun getCurrentMediaItemIndex(): Int {
        return 0
    }

    override fun getDuration(): Long {
        return duration
    }

    override fun getCurrentPosition(): Long {
        return currentPosition
    }

    override fun getBufferedPosition(): Long {
        return currentPosition
    }

    override fun getTotalBufferedDuration(): Long {
        return bufferedPosition
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun getContentPosition(): Long {
        return currentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return bufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return audioAttributes
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        if (this.audioAttributes == audioAttributes) {
            return
        }
        this.audioAttributes = audioAttributes
        listeners.sendEvent(EVENT_AUDIO_ATTRIBUTES_CHANGED) { listener ->
            listener.onAudioAttributesChanged(audioAttributes)
        }
    }

    override fun setVolume(volume: Float) {
        TODO("Not yet implemented")
    }

    override fun getVolume(): Float {
        return 1f
    }

    override fun mute() {
        MPVLib.setOptionString("mute", "yes")
    }

    override fun unmute() {
        MPVLib.setOptionString("mute", "no")
    }

    override fun clearVideoSurface() {
        clearVideoSurface(null)
    }

    override fun clearVideoSurface(surface: Surface?) {
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }

    override fun setVideoSurface(surface: Surface?) {
        if (surface != null) {
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
        }
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        if (surfaceHolder != null) {
            Log.w(TAG, "attaching surface")
            surfaceView.holder.addCallback(this)
            setVideoSurface(surfaceHolder.surface)
        }
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        if (surfaceHolder != null) {
            Log.w(TAG, "detaching surface")
            surfaceHolder.removeCallback(this)
            clearVideoSurface()
        }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        if (surfaceView != null) {
            if (backgroundColor != null) {
                surfaceView.setBackgroundColor(backgroundColor)
            }
            setVideoSurfaceHolder(surfaceView.holder)
            surfaceView.keepScreenOn = true
        }
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        clearVideoSurfaceHolder(surfaceView?.holder)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun getVideoSize(): VideoSize {
        return VideoSize.UNKNOWN
    }

    override fun getSurfaceSize(): Size {
        return surfaceView.holder.surfaceFrame.let {
            Size(it.width(), it.height())
        }
    }

    override fun getCurrentCues(): CueGroup {
        return CueGroup.EMPTY_TIME_ZERO
    }

    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo.UNKNOWN
    }

    override fun getDeviceVolume(): Int {
        TODO("Not yet implemented")
    }

    override fun isDeviceMuted(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun setDeviceVolume(volume: Int) {
        TODO("Not yet implemented")
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun increaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun decreaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun decreaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun setDeviceMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        TODO("Not yet implemented")
    }

    companion object {
        private const val TAG = "mpv"
    }
}
