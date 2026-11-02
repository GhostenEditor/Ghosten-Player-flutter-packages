package com.ghosten.videoplayer

import android.text.TextUtils
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.ui.TrackNameProvider

data class Subtitle(
    val url: String,
    val mimeType: String,
    val language: String?,
    val selected: Boolean,
    val label: String?,
)


data class Video(
    val url: String,
    val mimeType: String?,
    val title: String?,
    val description: String?,
    val poster: String?,
    val subtitle: List<Subtitle>?,
    var startPosition: Long,
    val endPosition: Long,
//    var skipEnd: PlayerMessage? = null,
//    var skipEndTip: PlayerMessage? = null,
)


fun Map<String, Any>.toVideo(): Video = Video(
    this[URL] as String,
    this[MIME_TYPE] as String?,
    this[TITLE] as String?,
    this[DESCRIPTION] as String?,
    this[POSTER] as String?,
    (this[SUBTITLE] as? List<*>)
        ?.filterIsInstance<Map<String, Any>>()
        ?.map {
            it.toSubtitle()
        },
    (this[START_POSITION] as Int? ?: 0).toLong(),
    (this[END_POSITION] as Int? ?: 0).toLong(),
)

private fun Map<String, Any>.toSubtitle(): Subtitle = Subtitle(
    this[URL] as String,
    this[MIME_TYPE] as String,
    this[LANGUAGE] as String?,
    this[SELECTED] as Boolean,
    this[LABEL] as String?
)

fun Video.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(this.url)
    .setMimeType(this.mimeType)
    .setSubtitleConfigurations((this.subtitle ?: listOf()).map {
        MediaItem.SubtitleConfiguration.Builder(it.url.toUri())
            .setId(it.url)
            .setMimeType(
                when (it.mimeType) {
                    "xml" -> MimeTypes.APPLICATION_TTML
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass" -> MimeTypes.TEXT_SSA
                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                    else -> throw Exception("Unknown Subtitle Mime Type")
                }
            )
            .setLanguage(it.language)
            .setLabel(it.label)
            .setSelectionFlags(
                if (it.selected) {
                    C.SELECTION_FLAG_AUTOSELECT
                } else {
                    C.SELECTION_FLAG_DEFAULT
                }
            )
            .build()
    })
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(this.title)
            .setDescription(this.description)
            .setArtist(this.description)
            .setArtworkUri(this.poster?.toUri())
            .build()
    )
    .build()

fun Map<String, Any>.toMediaItem(): MediaItem = this.toVideo().toMediaItem()

fun Format.toHashMap(selected: Boolean, trackNameProvider: TrackNameProvider): HashMap<String, Any?> = hashMapOf(
    "id" to id,
    "type" to if (TextUtils.isEmpty(sampleMimeType))
        MimeTypes.getTrackType(containerMimeType)
    else
        MimeTypes.getTrackType(sampleMimeType),
    "name" to trackNameProvider.getTrackName(this),
    "label" to label,
    "language" to language,
    "selected" to selected,
    "averageBitrate" to averageBitrate,
    "bitrate" to bitrate,
    "codecs" to codecs,
    "containerMimeType" to containerMimeType,
    "sampleMimeType" to sampleMimeType,
    "width" to width,
    "height" to height,
    "pixelFormat" to pixelFormat,
    "decodedWidth" to decodedWidth,
    "decodedHeight" to decodedHeight,
    "frameRate" to frameRate,
    "rotationDegrees" to rotationDegrees,
    "mirrorHorizontal" to mirrorHorizontal,
    "pixelWidthHeightRatio" to pixelWidthHeightRatio,
    "sampleRate" to sampleRate,
)

private const val URL: String = "url"
private const val TITLE: String = "title"
private const val DESCRIPTION: String = "description"
private const val POSTER: String = "poster"
private const val SUBTITLE: String = "subtitle"
private const val START_POSITION: String = "start"
private const val END_POSITION: String = "end"
private const val LANGUAGE: String = "language"
private const val MIME_TYPE: String = "mimeType"
private const val SELECTED: String = "selected"
private const val LABEL: String = "label"
