package com.fvprofile.core

import kotlin.math.abs
import kotlin.math.sqrt

const val G = 9.81

data class TrialMetrics(
    val tA: Double, val h: Double, val vTO: Double, val v: Double, val fN: Double, val fRel: Double,
    val pW: Double, val pRel: Double, val tPO: Double, val workJ: Double
)

/** Équations de Samozino et al. 2008 (J Biomech 41:2940) : F, v, P moyennes pendant la poussée. */
object Mech {
    fun heightFromFlight(tA: Double) = G * tA * tA / 8.0                       // eq. 11
    fun metrics(tA: Double, totalMass: Double, bodyMass: Double, hPO: Double): TrialMetrics {
        val h = heightFromFlight(tA)
        val vTO = sqrt(2 * G * h)                                              // eq. 7
        val v = sqrt(G * h / 2.0)                                              // eq. 8
        val f = totalMass * G * (h / hPO + 1.0)                                // eq. 4
        val p = f * v                                                          // eq. 9
        return TrialMetrics(tA, h, vTO, v, f, f / bodyMass, p, p / bodyMass,
            if (v > 1e-6) hPO / v else 0.0, totalMass * G * (hPO + h))         // eq. 5 et 2
    }
}

data class Fit(val slope: Double, val intercept: Double, val r2: Double)

fun linFit(x: List<Double>, y: List<Double>): Fit? {
    val n = x.size
    if (n < 2) return null
    val mx = x.average(); val my = y.average()
    var sxx = 0.0; var sxy = 0.0; var syy = 0.0
    for (i in 0 until n) { val dx = x[i] - mx; val dy = y[i] - my; sxx += dx * dx; sxy += dx * dy; syy += dy * dy }
    if (sxx < 1e-12) return null
    val slope = sxy / sxx
    val r2 = if (syy < 1e-12) 0.0 else (sxy * sxy) / (sxx * syy)
    return Fit(slope, my - slope * mx, r2)
}

enum class Category(val label: String) {
    HIGH_FORCE_DEFICIT("Déficit de force marqué"),
    LOW_FORCE_DEFICIT("Déficit de force modéré"),
    BALANCED("Profil équilibré"),
    LOW_VELOCITY_DEFICIT("Déficit de vitesse modéré"),
    HIGH_VELOCITY_DEFICIT("Déficit de vitesse marqué");
    val isForceDeficit get() = this == HIGH_FORCE_DEFICIT || this == LOW_FORCE_DEFICIT
    val isVelocityDeficit get() = this == HIGH_VELOCITY_DEFICIT || this == LOW_VELOCITY_DEFICIT
}

/** Seuils du Tableau 1 de Jiménez-Reyes et al. 2017 (profil exprimé en % de l'optimal). */
fun categoryOf(ratioPct: Double) = when {
    ratioPct < 60 -> Category.HIGH_FORCE_DEFICIT
    ratioPct <= 90 -> Category.LOW_FORCE_DEFICIT
    ratioPct <= 110 -> Category.BALANCED
    ratioPct <= 140 -> Category.LOW_VELOCITY_DEFICIT
    else -> Category.HIGH_VELOCITY_DEFICIT
}

/** Profil optimal : Samozino et al. 2012 (Med Sci Sports Exerc 44:313), éq. 6, saut vertical (α = 90°). */
object Optimal {
    fun vToMax(sfv: Double, pmax: Double, hPO: Double): Double {
        val f0 = sqrt(-4.0 * pmax * sfv)
        if (f0 <= G) return 0.0
        val inner = sfv * sfv / 4.0 + 2.0 / hPO * (f0 - G)
        return hPO * (sqrt(inner) + sfv / 2.0)
    }

    /** Sfv maximisant vTOmax pour (Pmax, hPO) : recherche numérique (équivalente à la solution analytique de l'annexe). */
    fun sfvOpt(pmax: Double, hPO: Double): Double {
        var bestS = Double.NaN; var bestV = -1.0
        val step = 0.01
        var s = -120.0
        while (s < -0.005) {
            val v = vToMax(s, pmax, hPO)
            if (v > bestV) { bestV = v; bestS = s }
            s += step
        }
        if (bestS.isNaN() || bestV <= 0) return Double.NaN
        var lo = bestS - step; var hi = bestS + step
        repeat(60) {
            val m1 = lo + (hi - lo) / 3; val m2 = hi - (hi - lo) / 3
            if (vToMax(m1, pmax, hPO) < vToMax(m2, pmax, hPO)) lo = m1 else hi = m2
        }
        return (lo + hi) / 2
    }
}

data class Profile(
    val f0: Double, val v0: Double, val pmax: Double, val sfv: Double, val r2: Double, val n: Int,
    val f0Abs: Double, val pmaxAbs: Double,
    val sfvOpt: Double, val ratioPct: Double, val imbalancePct: Double, val category: Category,
    val f0Opt: Double, val v0Opt: Double, val hModel: Double, val hOpt: Double, val gainPct: Double,
    val optTotalPctBm: Double, val optLoadKg: Double, val bodyMass: Double, val hPO: Double
)

/** v en m/s, f en N/kg de masse corporelle (force moyenne totale / masse du corps). */
fun buildProfile(v: List<Double>, f: List<Double>, bodyMass: Double, hPO: Double): Profile? {
    val fit = linFit(v, f) ?: return null
    if (fit.slope >= 0 || fit.intercept <= G) return null
    val f0 = fit.intercept; val sfv = fit.slope; val v0 = -f0 / sfv
    val pmax = f0 * v0 / 4.0
    val sOpt = Optimal.sfvOpt(pmax, hPO)
    if (sOpt.isNaN()) return null
    val ratio = sfv / sOpt * 100.0
    val f0Opt = sqrt(-4.0 * pmax * sOpt); val v0Opt = -f0Opt / sOpt
    val hModel = Optimal.vToMax(sfv, pmax, hPO).let { it * it / (2 * G) }
    val hOpt = Optimal.vToMax(sOpt, pmax, hPO).let { it * it / (2 * G) }
    // Charge maximisant la puissance : v = v0/2, F = F0/2 ; F = M (2v²/hPO + g)  (eq. 4 avec h = 2v²/g)
    val vp = v0 / 2.0
    val mOpt = (f0 * bodyMass / 2.0) / (2 * vp * vp / hPO + G)
    return Profile(f0, v0, pmax, sfv, fit.r2, v.size, f0 * bodyMass, pmax * bodyMass, sOpt, ratio, abs(100 - ratio),
        categoryOf(ratio), f0Opt, v0Opt, hModel, hOpt, if (hModel > 0) (hOpt / hModel - 1) * 100 else 0.0,
        mOpt / bodyMass * 100, mOpt - bodyMass, bodyMass, hPO)
}

data class Pt(val load: Int, val trial: Int, val v: Double, val f: Double, val h: Double)

/** Choisit, pour chaque charge, l'essai donnant la relation F-v la plus cohérente (R² maximal ; à égalité, plus grand saut). */
fun selectBest(cands: List<List<Pt>>): List<Pt> {
    val groups = cands.filter { it.isNotEmpty() }
    var best: List<Pt> = groups.map { it.first() }
    var bestR2 = -1.0; var bestH = -1.0
    val cur = ArrayList<Pt>()
    fun rec(i: Int) {
        if (i == groups.size) {
            val fit = linFit(cur.map { it.v }, cur.map { it.f })
            val r2 = if (fit != null && fit.slope < 0) fit.r2 else -1.0
            val sh = cur.sumOf { it.h }
            if (r2 > bestR2 + 1e-9 || (abs(r2 - bestR2) <= 1e-9 && sh > bestH)) { bestR2 = r2; bestH = sh; best = cur.toList() }
            return
        }
        for (p in groups[i]) { cur.add(p); rec(i + 1); cur.removeAt(cur.size - 1) }
    }
    if (groups.size in 1..14) rec(0)
    return best
}

data class TrialResult(
    val setIdx: Int, val trialIdx: Int, val loadKg: Double, val m: TrialMetrics,
    val knee: Double?, val hip: Double?, val selected: Boolean, val hErrPct: Double
)
data class Suspect(val setIdx: Int, val residual: Double, val r2Without: Double)
data class Diagnosis(val r2: Double?, val ok: Boolean, val suspects: List<Suspect>, val warnings: List<String>)
data class Computation(
    val trials: List<TrialResult>, val profile: Profile?, val diag: Diagnosis,
    val hUnloaded: Double?, val kneeMean: Double?, val hipMean: Double?
)

const val R2_TARGET = 0.98

fun compute(s: Session): Computation {
    class Raw(val si: Int, val ti: Int, val load: Double, val m: TrialMetrics, val t: TrialData, val err: Double)
    val raws = ArrayList<Raw>()
    val cands = ArrayList<List<Pt>>()
    s.sets.forEachIndexed { si, set ->
        val pts = ArrayList<Pt>()
        set.trials.forEachIndexed { ti, t ->
            val tFps = set.fpsFor(t)
            val tA = t.tA(tFps)
            val total = s.massKg + set.loadKg
            if (tA != null && tA in 0.1..1.2 && total > 10 && s.hPO > 0.1) {
                val m = Mech.metrics(tA, total, s.massKg, s.hPO)
                raws.add(Raw(si, ti, set.loadKg, m, t, 2.0 / (tFps * tA) * 100.0))
                pts.add(Pt(si, ti, m.v, m.fRel, m.h))
            }
        }
        if (pts.isNotEmpty()) cands.add(pts)
    }
    val sel = selectBest(cands)
    val selKeys = sel.map { it.load to it.trial }.toSet()
    val trials = raws.map { TrialResult(it.si, it.ti, it.load, it.m, it.t.kneeDeg, it.t.hipDeg, (it.si to it.ti) in selKeys, it.err) }

    val warnings = ArrayList<String>()
    var profile: Profile? = null
    var r2: Double? = null
    val suspects = ArrayList<Suspect>()
    if (sel.size >= 3) {
        profile = buildProfile(sel.map { it.v }, sel.map { it.f }, s.massKg, s.hPO)
        val fit = linFit(sel.map { it.v }, sel.map { it.f })
        r2 = fit?.r2
        if (profile == null) warnings.add("Relation F-v inexploitable (pente positive ou F0 trop faible) : vérifiez les charges et les vidéos.")
        if (r2 != null && r2 < R2_TARGET) {
            val list = ArrayList<Suspect>()
            for (i in sel.indices) {
                val others = sel.filterIndexed { j, _ -> j != i }
                if (others.size < 3) continue
                val f2 = linFit(others.map { it.v }, others.map { it.f }) ?: continue
                if (f2.slope >= 0) continue
                list.add(Suspect(sel[i].load, sel[i].f - (f2.slope * sel[i].v + f2.intercept), f2.r2))
            }
            suspects.addAll(list.sortedByDescending { it.r2Without }.filter { it.r2Without > r2 + 0.005 }.take(2))
        }
    } else if (sel.isNotEmpty()) warnings.add("Au moins 3 charges analysées sont nécessaires pour tracer le profil (5 recommandées).")
    if (sel.size in 3..4) warnings.add("Moins de 5 charges : profil peu robuste (≥ 5 charges recommandées, Morin & Samozino 2016).")
    if (sel.isNotEmpty() && sel.none { s.sets[it.load].loadKg == 0.0 }) warnings.add("Aucun saut sans charge (0 kg) : à inclure dans le profil.")
    if (sel.isNotEmpty()) {
        val heaviest = sel.maxByOrNull { s.sets[it.load].loadKg }!!
        if (heaviest.h > 0.15) warnings.add("La charge la plus lourde permet encore ${"%.0f".format(heaviest.h * 100)} cm de saut : ajoutez une charge plus lourde (objectif ≈ 10 cm).")
    }
    val worst = trials.filter { it.selected }.maxOfOrNull { it.hErrPct }
    if (worst != null && worst > 5.0) warnings.add("Cadence vidéo faible : incertitude possible de ±${"%.0f".format(worst)} % sur la hauteur de saut (±1 image). Filmez à 120 ips ou plus.")
    val knees = trials.mapNotNull { it.knee }
    val hips = trials.mapNotNull { it.hip }
    if (knees.size >= 2) {
        val km = knees.average(); val sd = sqrt(knees.sumOf { (it - km) * (it - km) } / (knees.size - 1))
        if (abs(km - 90) > 10) warnings.add("Angle de genou moyen au départ : ${"%.0f".format(km)}° (cible ≈ 90°). hPO suppose une position de départ standardisée.")
        if (sd > 5) warnings.add("Angle de genou variable entre essais (σ = ${"%.1f".format(sd)}°) : position de départ non standardisée.")
    }
    val h0 = trials.filter { it.selected }.minByOrNull { abs(it.loadKg) }?.takeIf { abs(it.loadKg) < 0.5 }?.m?.h
    val ok = r2 != null && r2 >= R2_TARGET && profile != null
    return Computation(trials, profile, Diagnosis(r2, ok, suspects, warnings), h0, knees.takeIf { it.isNotEmpty() }?.average(), hips.takeIf { it.isNotEmpty() }?.average())
}
