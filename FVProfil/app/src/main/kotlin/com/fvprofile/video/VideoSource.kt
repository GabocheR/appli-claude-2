package com.fvprofile.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlin.math.max

/** Lecture image par image (index d'image → temps = index / cadence réelle). */
class VideoSource(val path: String) {
    private val r = MediaMetadataRetriever().apply { setDataSource(path) }
    val frameCount: Int
    val fpsDetected: Double
    private val cache = object : LinkedHashMap<Long, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, Bitmap>?) = size > 24
    }

    init {
        val fc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0
        val cap = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
        frameCount = fc
        val calc = if (fc > 0 && dur > 0) fc * 1000.0 / dur else 0.0
        fpsDetected = if (calc > 0) calc else (cap ?: 30.0)
    }

    fun frame(index: Int, maxSide: Int): Bitmap? = synchronized(r) {
        val i = index.coerceIn(0, max(0, frameCount - 1))
        val key = i.toLong() * 10000 + maxSide
        cache[key]?.let { return it }
        val full = try { r.getFrameAtIndex(i) } catch (e: Exception) { null } ?: return null
        val s = maxSide.toFloat() / max(full.width, full.height)
        val out = if (s < 1f) Bitmap.createScaledBitmap(full, (full.width * s).toInt(), (full.height * s).toInt(), true) else full
        if (out !== full) full.recycle()
        cache[key] = out
        out
    }

    fun close() { try { r.release() } catch (_: Exception) {} }
}
