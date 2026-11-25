package com.fvprofile.video

import android.graphics.Bitmap
import com.fvprofile.core.Landmarks
import com.fvprofile.core.P
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.tasks.await

/** Détection de posture (ML Kit, modèle précis embarqué). Choisit le côté du corps visible par la caméra. */
class PoseTool {
    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder().setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE).build()
    )

    suspend fun detect(b: Bitmap): Landmarks? {
        val pose = try { detector.process(InputImage.fromBitmap(b, 0)).await() } catch (e: Exception) { return null }
        fun p(l: PoseLandmark?) = l?.let { P(it.position.x.toDouble(), it.position.y.toDouble()) }
        fun side(sh: Int, hp: Int, kn: Int, an: Int, he: Int, to: Int): Pair<Double, Landmarks?> {
            val l = listOf(sh, hp, kn, an).map { pose.getPoseLandmark(it) }
            if (l.any { it == null }) return 0.0 to null
            val score = l.sumOf { it!!.inFrameLikelihood.toDouble() }
            return score to Landmarks(p(l[0])!!, p(l[1])!!, p(l[2])!!, p(l[3])!!, p(pose.getPoseLandmark(to)), p(pose.getPoseLandmark(he)))
        }
        val left = side(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX)
        val right = side(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)
        val best = if (left.first >= right.first) left else right
        return if (best.first < 2.0) null else best.second
    }

    fun close() { try { detector.close() } catch (_: Exception) {} }
}
