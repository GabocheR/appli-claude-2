package com.fvprofile.core

import kotlin.math.acos
import kotlin.math.hypot

data class WeightEntry(val date: Long, val kg: Double)

data class Athlete(
    val id: String, val name: String, val sex: String, val birth: String, val sport: String,
    val heightCm: Double, val massKg: Double, val legLengthM: Double, val startHeightM: Double, val notes: String,
    val weights: List<WeightEntry> = emptyList()
) {
    /** Distance de poussée hPO = longueur du membre inférieur étendu − hauteur de départ (Samozino 2008). */
    val hPO: Double get() = legLengthM - startHeightM
}

data class TrialData(
    val startFrame: Int? = null, val takeoff: Int? = null, val landing: Int? = null,
    val kneeDeg: Double? = null, val hipDeg: Double? = null,
    /** 0 = marqueurs relatifs à la vidéo 1 de la charge, 1 = relatifs à la vidéo 2 (essai filmé à part). */
    val src: Int = 0
) {
    fun tA(fps: Double): Double? {
        val a = takeoff; val b = landing
        if (a == null || b == null || fps <= 0.0 || b <= a) return null
        return (b - a) / fps
    }
}

data class LoadSet(
    val id: String, val loadKg: Double, val videoPath: String? = null, val fps: Double = 0.0,
    val trials: List<TrialData> = listOf(TrialData(), TrialData()),
    val video2Path: String? = null, val fps2: Double = 0.0
) {
    fun fpsFor(t: TrialData): Double = if (t.src == 1) fps2 else fps
}

data class Session(
    val id: String, val athleteId: String, val date: Long, val massKg: Double, val hPO: Double,
    val auto: Boolean, val cameraLabel: String, val sets: List<LoadSet>, val name: String = ""
)

data class P(val x: Double, val y: Double)
data class Landmarks(val shoulder: P, val hip: P, val knee: P, val ankle: P, val toe: P?, val heel: P?)

/** Angle intérieur (°) au sommet [v] entre les segments v→a et v→b. 180° = segment étendu. */
fun angleAt(a: P, v: P, b: P): Double {
    val ax = a.x - v.x; val ay = a.y - v.y; val bx = b.x - v.x; val by = b.y - v.y
    val n = hypot(ax, ay) * hypot(bx, by)
    if (n < 1e-9) return 0.0
    return Math.toDegrees(acos(((ax * bx + ay * by) / n).coerceIn(-1.0, 1.0)))
}
fun kneeAngle(l: Landmarks) = angleAt(l.hip, l.knee, l.ankle)
fun hipAngle(l: Landmarks) = angleAt(l.shoulder, l.hip, l.knee)
