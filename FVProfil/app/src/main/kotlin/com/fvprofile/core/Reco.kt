package com.fvprofile.core

/** Exercices et charges : Tableau 2 de Jiménez-Reyes et al. 2017. PC = poids de corps. */
enum class Quality(val label: String, val exercises: List<Pair<String, String>>) {
    STRENGTH("Force", listOf("Squat arrière" to "80–90 % 1RM", "Presse à cuisses" to "90–95 % 1RM", "Soulevé de terre" to "90–95 % 1RM")),
    STRENGTH_POWER("Force-puissance", listOf("Épaulé tiré (clean pull)" to "80 % 1RM", "Soulevé de terre" to "80 % 1RM", "Squat jump chargé" to "> 70 % du PC", "CMJ chargé" to "> 80 % du PC")),
    POWER("Puissance", listOf("Squat jump chargé" to "20–30 % du PC", "CMJ chargé" to "35–45 % du PC", "Squat jump unipodal" to "poids de corps", "CMJ unipodal" to "10 % du PC", "Épaulé tiré sauté (clean pull jump)" to "65 % 1RM")),
    POWER_SPEED("Puissance-vitesse", listOf("Depth jumps (sauts en contrebas)" to "poids de corps", "Squat jump" to "poids de corps", "CMJ" to "10 % du PC", "Saut sur box vertical maximal" to "—")),
    SPEED("Vitesse", listOf("Poussée horizontale maximale sur chariot (roller push-off)" to "charge < poids de corps", "CMJ avec bras" to "poids de corps"))
}

data class Slot(val quality: Quality, val count: Int)

object Reco {
    /** Répartition des 6 exercices hebdomadaires (3 séries chacun = 18 séries/sem) : Tableau 1 de Jiménez-Reyes et al. 2017. */
    fun plan(c: Category): List<Slot> = when (c) {
        Category.HIGH_FORCE_DEFICIT -> listOf(Slot(Quality.STRENGTH, 3), Slot(Quality.STRENGTH_POWER, 2), Slot(Quality.POWER, 1))
        Category.LOW_FORCE_DEFICIT -> listOf(Slot(Quality.STRENGTH, 2), Slot(Quality.STRENGTH_POWER, 2), Slot(Quality.POWER, 2))
        Category.BALANCED -> listOf(Slot(Quality.STRENGTH, 1), Slot(Quality.STRENGTH_POWER, 1), Slot(Quality.POWER, 2), Slot(Quality.POWER_SPEED, 1), Slot(Quality.SPEED, 1))
        Category.LOW_VELOCITY_DEFICIT -> listOf(Slot(Quality.SPEED, 2), Slot(Quality.POWER_SPEED, 2), Slot(Quality.POWER, 2))
        Category.HIGH_VELOCITY_DEFICIT -> listOf(Slot(Quality.SPEED, 3), Slot(Quality.POWER_SPEED, 2), Slot(Quality.POWER, 1))
    }

    /** Deux séances types (A et B), 3 exercices × 3 séries chacune. */
    fun sessions(c: Category): Pair<List<String>, List<String>> {
        val a = ArrayList<Pair<Quality, String>>(); val b = ArrayList<Pair<Quality, String>>()
        var k = 0
        for (slot in plan(c)) for (i in 0 until slot.count) {
            val ex = slot.quality.exercises[i % slot.quality.exercises.size]
            val txt = "${ex.first} — ${ex.second}" + if (i >= slot.quality.exercises.size) " (variante)" else ""
            (if (k % 2 == 0) a else b).add(slot.quality to "$txt · 3 séries")
            k++
        }
        return a.sortedBy { it.first.ordinal }.map { it.second } to b.sortedBy { it.first.ordinal }.map { it.second }
    }
}
