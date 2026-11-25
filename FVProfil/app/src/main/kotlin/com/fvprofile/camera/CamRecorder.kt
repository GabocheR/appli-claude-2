package com.fvprofile.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import java.io.File

data class CamOption(val cameraId: String, val camName: String, val w: Int, val h: Int, val fps: Int, val highSpeed: Boolean, val confirmed: Boolean = true) {
    val label: String get() = "${w}×${h} · $fps ips" + (if (highSpeed) " (ralenti)" else "") + (if (confirmed) "" else " (?)")
    val key: String get() = "$cameraId|$w|$h|$fps|$highSpeed"
}

object CamOptions {
    private fun encoders(): List<MediaCodecInfo.VideoCapabilities> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals("video/avc", true) } }
            .mapNotNull { runCatching { it.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull() }
    } catch (_: Exception) { emptyList() }

    /** Modes déclarés par la caméra arrière du téléphone (Camera2) et acceptés par AU MOINS UN codeur H.264 ;
     *  + profils vidéo officiels du constructeur (CamcorderProfile). Avec [includeUnconfirmed], ajoute des modes
     *  possibles mais non déclarés (marqués « ? ») que l'on peut essayer. */
    fun list(ctx: Context, includeUnconfirmed: Boolean = false): List<CamOption> {
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val enc = encoders()
        val out = LinkedHashMap<String, CamOption>()
        fun encOk(w: Int, h: Int, fps: Int) = enc.isEmpty() || enc.any {
            runCatching { it.areSizeAndRateSupported(w, h, fps.toDouble()) || it.areSizeAndRateSupported(h, w, fps.toDouble()) }.getOrDefault(false)
        }
        fun add(o: CamOption) { if (o.key !in out || (o.confirmed && out[o.key]?.confirmed == false)) out[o.key] = o }
        try {
            for (id in mgr.cameraIdList) {
                val ch = mgr.getCameraCharacteristics(id)
                if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                val name = "Caméra $id" + (focal?.let { " (f = ${"%.1f".format(it)} mm)" } ?: "")
                val ranges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
                val fpsSet = ranges.map { it.upper }.filter { it >= 15 }.toSortedSet()
                val sizes = (map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()).filter { it.width >= 640 && it.width >= it.height }
                for (s in sizes) {
                    val d1 = runCatching { map.getOutputMinFrameDuration(MediaRecorder::class.java, s) }.getOrNull() ?: 0L
                    val d2 = runCatching { map.getOutputMinFrameDuration(android.graphics.SurfaceTexture::class.java, s) }.getOrNull() ?: 0L
                    val durs = listOf(d1, d2).filter { it > 0L }
                    val maxF = durs.minOrNull()?.let { (1e9 / it + 0.5).toInt() } ?: 30
                    for (f in fpsSet) if (f <= maxF && encOk(s.width, s.height, f)) add(CamOption(id, name, s.width, s.height, f, false))
                    if (includeUnconfirmed) for (f in listOf(24, 30, 60, 120)) if (encOk(s.width, s.height, f)) add(CamOption(id, name, s.width, s.height, f, false, confirmed = false))
                }
                // Profils vidéo officiels du constructeur
                id.toIntOrNull()?.let { idInt ->
                    for (q in listOf(CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_2160P)) {
                        runCatching { CamcorderProfile.get(idInt, q) }.getOrNull()?.let { p ->
                            if (p.videoFrameWidth >= 640 && encOk(p.videoFrameWidth, p.videoFrameHeight, p.videoFrameRate))
                                add(CamOption(id, name, p.videoFrameWidth, p.videoFrameHeight, p.videoFrameRate, false))
                        }
                    }
                }
                for (s in map.highSpeedVideoSizes ?: emptyArray()) {
                    for (r in map.getHighSpeedVideoFpsRangesFor(s)) if (r.upper >= 60 && encOk(s.width, s.height, r.upper)) add(CamOption(id, name, s.width, s.height, r.upper, true))
                }
            }
        } catch (_: Exception) {}
        return out.values.sortedWith(compareBy<CamOption> { it.cameraId }.thenBy { !it.confirmed }.thenByDescending { it.highSpeed }.thenByDescending { it.fps }.thenByDescending { it.w })
    }

    /** Plage de compensation d'exposition de la caméra : (min, max, pas en EV). (0,0,0) si non supportée. */
    fun exposureInfo(ctx: Context, cameraId: String): Triple<Int, Int, Float> = try {
        val ch = (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(cameraId)
        val r = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val st = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        if (r == null || st == null) Triple(0, 0, 0f) else Triple(r.lower, r.upper, st.toFloat())
    } catch (_: Exception) { Triple(0, 0, 0f) }

    /** Texte technique brut : ce que le téléphone déclare réellement (utile pour comprendre un mode manquant). */
    fun diagnostic(ctx: Context): String {
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder()
        try {
            val enc = encoders()
            sb.append("Codeurs H.264 : ${enc.size}\n")
            enc.forEachIndexed { i, c -> sb.append("  #$i fréquences ${c.supportedFrameRates} · tailles ${c.supportedWidths}×${c.supportedHeights}\n") }
            for (id in mgr.cameraIdList) {
                val ch = mgr.getCameraCharacteristics(id)
                val facing = if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) "arrière" else "autre"
                val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                sb.append("\nCaméra $id ($facing)\n")
                sb.append("  Plages d'ips déclarées : ${ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString(" ")}\n")
                if (map != null) {
                    val sz = (map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()).filter { it.width >= 1280 }
                    sb.append("  Tailles vidéo ≥ 720p : ${sz.joinToString(", ") { "${it.width}x${it.height}" }}\n")
                    sb.append("  Ralenti : ${(map.highSpeedVideoSizes ?: emptyArray()).joinToString(", ") { s -> "${s.width}x${s.height}@" + map.getHighSpeedVideoFpsRangesFor(s).joinToString("/") { it.upper.toString() } }}\n")
                }
            }
        } catch (e: Exception) { sb.append("Erreur : ${e.message}") }
        return sb.toString()
    }
}

class CamRecorder(private val ctx: Context, private val opt: CamOption, val outFile: File) {
    private val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("cam").also { it.start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var released = false
    private var reqBuilder: CaptureRequest.Builder? = null
    private var exposureIdx = 0
    var recording = false; private set
    /** Appelé (thread principal) si le système arrête l'enregistrement (durée/taille max, erreur) : le fichier est à sauvegarder. */
    var onSystemStop: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun open(preview: Surface, deviceRotationDeg: Int, exposure: Int, ready: () -> Unit, fail: (String) -> Unit) {
        exposureIdx = exposure
        try {
            val ch = mgr.getCameraCharacteristics(opt.cameraId)
            val sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setOutputFile(outFile.absolutePath)
            mr.setVideoEncodingBitRate((opt.w.toLong() * opt.h * opt.fps * 0.12).toInt().coerceIn(8_000_000, 100_000_000))
            mr.setVideoFrameRate(opt.fps)
            mr.setVideoSize(opt.w, opt.h)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setOrientationHint((sensor - deviceRotationDeg + 360) % 360)
            // Aucune limite de durée ni de taille imposée par l'application
            mr.setMaxDuration(0); mr.setMaxFileSize(0)
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
                    main.post { onSystemStop?.invoke() }
            }
            mr.prepare()
            recorder = mr
            mgr.openCamera(opt.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) { device = cam; startSession(cam, ch, preview, mr.surface, ready, fail) }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); main.post { if (recording) onSystemStop?.invoke() else fail("Caméra déconnectée") } }
                override fun onError(cam: CameraDevice, error: Int) { cam.close(); main.post { if (recording) onSystemStop?.invoke() else fail("Erreur caméra ($error)") } }
            }, handler)
        } catch (e: Exception) { fail("Impossible d'ouvrir ce format : ${e.message}") }
    }

    @Suppress("DEPRECATION")
    private fun startSession(cam: CameraDevice, ch: CameraCharacteristics, preview: Surface, rec: Surface, ready: () -> Unit, fail: (String) -> Unit) {
        try {
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val ranges: Array<Range<Int>> = if (opt.highSpeed) map.getHighSpeedVideoFpsRangesFor(Size(opt.w, opt.h))
                else ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            val range = ranges.firstOrNull { it.lower == opt.fps && it.upper == opt.fps }
                ?: ranges.firstOrNull { it.upper == opt.fps } ?: Range(opt.fps, opt.fps)
            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(preview); addTarget(rec); set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                val er = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (er != null && (er.lower != 0 || er.upper != 0)) set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureIdx.coerceIn(er.lower, er.upper))
            }
            reqBuilder = req
            val surfaces = listOf(preview, rec)
            if (opt.highSpeed) {
                cam.createConstrainedHighSpeedCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        try {
                            val hs = s as CameraConstrainedHighSpeedCaptureSession
                            hs.setRepeatingBurst(hs.createHighSpeedRequestList(req.build()), null, handler)
                            main.post { ready() }
                        } catch (e: Exception) { main.post { fail("Ralenti indisponible : ${e.message}") } }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) { main.post { fail("Configuration ralenti refusée par le téléphone") } }
                }, handler)
            } else {
                cam.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        try { s.setRepeatingRequest(req.build(), null, handler); main.post { ready() } }
                        catch (e: Exception) { main.post { fail("Erreur d'aperçu : ${e.message}") } }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) { main.post { fail("Configuration caméra refusée") } }
                }, handler)
            }
        } catch (e: Exception) { main.post { fail("Erreur caméra : ${e.message}") } }
    }

    /** Modifie l'exposition (indice de compensation) en direct, aperçu et enregistrement. */
    fun setExposure(idx: Int) {
        val b = reqBuilder ?: return
        handler.post {
            try {
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, idx)
                val sess = session ?: return@post
                if (opt.highSpeed) { val hs = sess as CameraConstrainedHighSpeedCaptureSession; hs.setRepeatingBurst(hs.createHighSpeedRequestList(b.build()), null, handler) }
                else sess.setRepeatingRequest(b.build(), null, handler)
            } catch (_: Exception) {}
        }
    }

    fun start(): Boolean = try { recorder?.start(); recording = true; true } catch (e: Exception) { false }

    /** Arrête l'enregistrement ; renvoie true si le fichier est exploitable. */
    fun stopAndRelease(): Boolean {
        var ok = false
        try {
            if (recording) {
                try { session?.stopRepeating(); session?.abortCaptures() } catch (_: Exception) {}
                recorder?.stop(); ok = true
            }
        } catch (_: Exception) { ok = false }
        recording = false
        release()
        if (!ok) outFile.delete()
        return ok
    }

    fun release() {
        if (released) return
        released = true
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        thread.quitSafely()
    }
}
