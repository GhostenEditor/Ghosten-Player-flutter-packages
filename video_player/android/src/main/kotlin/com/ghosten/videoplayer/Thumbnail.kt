package com.ghosten.videoplayer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ThumbnailThread(private val context: Context) : Thread() {
    private val playerDB: PlayerDatabaseHelper = PlayerDatabaseHelper(context)
    private val tasks: MutableList<() -> Unit> = mutableListOf()
    private var shouldLoop = true
    private var retriever: MediaMetadataRetriever? = null
    private var url: String? = null
    override fun run() {
        while (shouldLoop) {
            if (tasks.isEmpty()) {
                sleep(1000L)
            }
            while (shouldLoop && tasks.isNotEmpty()) {
                Log.d("Retriever Tasks Count", tasks.size.toString())
                val task = tasks[0]
                task()
                tasks.remove(task)
            }
        }
    }

    fun getUrl(url: String, timeMs: Long, callback: (String?) -> Unit) {
        val path: String? = playerDB.queryPath(url, timeMs)
        if (path != null) {
            if (File(context.cacheDir, path).exists()) {
                callback(context.cacheDir.toString() + "/" + path)
            } else {
                playerDB.delete(url, timeMs)
                add(url, timeMs, callback)
            }
        } else {
            add(url, timeMs, callback)
        }
    }

    fun add(path: String, timeMs: Long, callback: (String?) -> Unit) {
        val task = {
            if (url != path) {
                url = path
                retriever?.release()
                retriever = MediaMetadataRetriever()
                try {
                    if (url!!.startsWith("content://", ignoreCase = true)) {
                        retriever?.setDataSource(context, url!!.toUri())
                    } else if (url!!.startsWith("file://", ignoreCase = true)) {
                        retriever?.setDataSource(url)
                    } else {
                        retriever?.setDataSource(url, HashMap())

                    }
                } catch (e: Exception) {
                    retriever = null
                    Log.e("MediaMetadataRetriever", e.toString())
                }
            }

            val thumbnailBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever?.getScaledFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    600,
                    400
                )
            } else {
                retriever?.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            }

            val path = thumbnailBitmap?.let {
                val filename = UUID.randomUUID().toString() + ".jpg"
                val file = File(context.cacheDir, filename)
                val fos = FileOutputStream(file)
                it.compress(Bitmap.CompressFormat.JPEG, 75, fos)
                fos.flush()
                fos.close()
                playerDB.insert(url!!, timeMs, filename)
                context.cacheDir.toString() + "/" + filename
            }
            callback(path)
        }
        if (tasks.size > 3) {
            for (i in 3 until tasks.size) {
                tasks.removeAt(i)
            }
        }
        tasks.add(0, task)
    }

    fun clear() {
        tasks.clear()
    }

    fun cancel() {
        playerDB.close()
        shouldLoop = false
        retriever?.release()
        retriever = null
        tasks.clear()
    }
}
