@file:OptIn(ExperimentalMaterial3Api::class)

package com.fvprofile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fvprofile.core.*
import com.fvprofile.ui.*

@Composable
fun ResultsScreen(vm: AppVM, sid: String, onMenu: () -> Unit) {
    val session = vm.session(sid) ?: return
    val a = vm.athlete(session.athleteId)
    val c = remember(session) { compute(session) }
    val p = c.profile
    PageV(vm, "Profil force-vitesse", onMenu) {
        Text(sessionTitle(session), color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Body("${a?.name ?: ""} · ${fmtDT(session.date)} · ${fmt(session.massKg, 1)} kg · hPO ${fmt(session.hPO * 100, 1)} cm", size = 13)
        if (p == null) {
            Card2 { H("Profil non calculable"); c.diag.warnings.forEach { Body("• $it", Warn) }
                BtnPrimary("Retour aux charges", { vm.pop() }) }
            return@PageV
        }
        val r2ok = p.r2 >= R2_TARGET
        Card2 {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Pmax", fmt(p.pmax, 1), "W/kg", Modifier.weight(1f), RedBright)
                Stat("R²", fmt(p.r2, 3), "", Modifier.weight(1f), if (r2ok) Ok else Warn)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("F0", fmt(p.f0, 1), "N/kg", Modifier.weight(1f)); Stat("V0", fmt(p.v0, 2), "m/s", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Profil / optimal", fmt(p.ratioPct, 0), "%", Modifier.weight(1f), RedBright)
                Stat("Saut sans charge", c.hUnloaded?.let { fmt(it * 100, 1) } ?: "—", "cm", Modifier.weight(1f))
            }
            Text(p.category.label, color = RedBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            RefTag(Refs.M16)
        }
        // Qualité
        Card2 {
            H(if (r2ok) "Qualité : relation F-v valide" else "Qualité : R² < ${fmt(R2_TARGET, 2)} — à refaire")
            Body("R² = ${fmt(p.r2, 3)} avec ${p.n} charges (objectif ≥ ${fmt(R2_TARGET, 2)} ; sinon refaire les charges incohérentes).", if (r2ok) TextMid else Warn)
            c.diag.suspects.forEach { s ->
                val set = session.sets[s.setIdx]
                Body("Charge ${fmt(set.loadKg, 1)} kg suspecte : écart de ${fmt(s.residual, 1)} N/kg à la droite ; sans elle R² = ${fmt(s.r2Without, 3)}.", Warn)
                BtnOutline("Refaire la charge ${fmt(set.loadKg, 1)} kg", {
                    vm.resetSet(sid, set.id)
                    vm.replace(Screen.Record(sid, set.id, 0))
                })
            }
            c.diag.warnings.forEach { Body("• $it", Warn, 13) }
            Body("Pour chaque charge, l'essai retenu est celui qui rend la relation F-v la plus cohérente (R² maximal).", size = 13)
        }
        // Graphique F-v
        val sel = c.trials.filter { it.selected }; val rej = c.trials.filter { !it.selected }
        Card2 {
            H("Relation force-vitesse")
            XYChart(listOf(
                Series(listOf(0.0 to p.f0, p.v0 to 0.0), RedBright, "Profil actuel", dots = false, line = true),
                Series(listOf(0.0 to p.f0Opt, p.v0Opt to 0.0), TextHi, "Profil optimal", dots = false, line = true, dashed = true),
                Series(sel.map { it.m.v to it.m.fRel }, RedBright, "Essais retenus"),
                Series(rej.map { it.m.v to it.m.fRel }, TextMid, "Non retenus", hollow = true)
            ), "vitesse moyenne (m/s)", "force (N/kg)", xMinZero = true, yMinZero = true)
            Body("Même Pmax (${fmt(p.pmax, 1)} W/kg) pour les deux droites : seule la pente change.", size = 13)
            RefTag(Refs.S12)
        }
        Card2 {
            H("Relation puissance-vitesse")
            fun curve(f0: Double, v0: Double) = (0..40).map { val v = v0 * it / 40; v to f0 * v * (1 - v / v0) }
            XYChart(listOf(
                Series(curve(p.f0, p.v0), RedBright, "Profil actuel", dots = false, line = true),
                Series(curve(p.f0Opt, p.v0Opt), TextHi, "Profil optimal", dots = false, line = true, dashed = true),
                Series(sel.map { it.m.v to it.m.pRel }, RedBright, "Essais retenus")
            ), "vitesse (m/s)", "puissance (W/kg)", xMinZero = true, yMinZero = true)
        }
        // Interprétation
        Card2 {
            H("Interprétation")
            Body("Pente actuelle Sfv = ${fmt(p.sfv, 2)} N·s/m/kg ; pente optimale = ${fmt(p.sfvOpt, 2)}. Le profil est à ${fmt(p.ratioPct, 0)} % de l'optimal (écart ${fmt(p.imbalancePct, 0)} %).", TextHi)
            Body(when {
                p.category.isForceDeficit -> "Profil trop orienté vers la vitesse : la capacité de force (F0) est le facteur limitant."
                p.category.isVelocityDeficit -> "Profil trop orienté vers la force : la vitesse d'extension (V0) est le facteur limitant."
                else -> "Profil proche de l'optimal : l'enjeu principal est d'augmenter Pmax en conservant l'équilibre."
            })
            Body("Avec la même Pmax, un profil optimal permettrait un saut de ${fmt(p.hOpt * 100, 1)} cm au lieu de ${fmt(p.hModel * 100, 1)} cm (modèle), soit ${if (p.gainPct >= 0) "+" else ""}${fmt(p.gainPct, 1)} %.")
            Body("Charge totale maximisant la puissance : ${fmt(p.optTotalPctBm, 0)} % du poids de corps (${if (p.optLoadKg >= 0) "+" else ""}${fmt(p.optLoadKg, 1)} kg ${if (p.optLoadKg < 0) "d'allègement" else "ajoutés"}).", size = 13)
            RefTag(Refs.S12)
            BtnPrimary("Recommandations d'entraînement", { vm.push(Screen.Reco(sid)) }, Modifier.fillMaxWidth())
        }
        // Angles
        if (c.kneeMean != null || c.hipMean != null) Card2 {
            H("Angles articulaires au départ")
            Body("Moyenne : genou ${fmt(c.kneeMean, 0)}° · hanche ${fmt(c.hipMean, 0)}° (180° = extension complète).")
            c.trials.sortedWith(compareBy({ it.loadKg }, { it.trialIdx })).forEach {
                Body("${fmt(it.loadKg, 1)} kg · essai ${it.trialIdx + 1} : genou ${fmt(it.knee, 0)}° · hanche ${fmt(it.hip, 0)}°", size = 13)
            }
            Body("Une position de départ identique (≈ 90° de genou) entre essais est nécessaire pour que hPO soit valable.", size = 13)
            RefTag(Refs.S08)
        }
        Expand("Variables du profil (détail)") {
            val rows = listOf(
                "F0 (N/kg)" to fmt(p.f0, 2), "F0 absolue (N)" to fmt(p.f0Abs, 0), "V0 (m/s)" to fmt(p.v0, 2),
                "Pmax = F0·V0/4 (W/kg)" to fmt(p.pmax, 2), "Pmax absolue (W)" to fmt(p.pmaxAbs, 0),
                "Sfv = −F0/V0" to fmt(p.sfv, 2), "Sfv optimal" to fmt(p.sfvOpt, 2), "Profil / optimal (%)" to fmt(p.ratioPct, 1),
                "Déséquilibre |100 − profil| (%)" to fmt(p.imbalancePct, 1), "F0 optimale (N/kg)" to fmt(p.f0Opt, 2), "V0 optimale (m/s)" to fmt(p.v0Opt, 2),
                "R² de la régression" to fmt(p.r2, 4), "Hauteur modèle (cm)" to fmt(p.hModel * 100, 1), "Hauteur au profil optimal (cm)" to fmt(p.hOpt * 100, 1),
                "Gain potentiel (%)" to fmt(p.gainPct, 1), "Charge optimale de puissance (kg)" to fmt(p.optLoadKg, 1), "hPO (cm)" to fmt(p.hPO * 100, 1)
            )
            rows.forEach { (k, v) -> Row(Modifier.fillMaxWidth()) { Text(k, color = TextMid, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(v, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
        }
        Expand("Détail de chaque saut") {
            Body("F, v, P = valeurs moyennes pendant la poussée (Samozino 2008). W = travail total ; tPO = durée de poussée ; ± = incertitude de ±1 image sur la hauteur.", size = 12)
            RefTag(Refs.S08)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                val w = listOf(58, 44, 52, 56, 52, 58, 58, 62, 52, 50, 50, 46, 44)
                @Composable fun row(cells: List<String>, bold: Boolean) = Row {
                    cells.forEachIndexed { i, s -> Text(s, color = if (bold) RedBright else TextHi, fontSize = 11.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(w[i].dp)) }
                }
                row(listOf("Charge kg", "Essai", "tA s", "h cm", "vTO m/s", "v m/s", "F N", "F N/kg", "P W", "P W/kg", "tPO s", "W J", "±%"), true)
                c.trials.sortedWith(compareBy({ it.loadKg }, { it.trialIdx })).forEach { t ->
                    row(listOf(fmt(t.loadKg, 1), "${t.trialIdx + 1}${if (t.selected) " ✓" else ""}", fmt(t.m.tA, 3), fmt(t.m.h * 100, 1), fmt(t.m.vTO, 2), fmt(t.m.v, 2),
                        fmt(t.m.fN, 0), fmt(t.m.fRel, 1), fmt(t.m.pW, 0), fmt(t.m.pRel, 1), fmt(t.m.tPO, 3), fmt(t.m.workJ, 0), fmt(t.hErrPct, 1)), false)
                }
            }
        }
        val vBytes = vm.videoBytes(sid)
        var delVid by remember { mutableStateOf(false) }
        Card2 {
            H("Vidéos")
            Body(if (vBytes > 0) "Les vidéos occupent ${fmt(vBytes / 1e6, 0)} Mo. Vous pouvez les supprimer : le profil, les marqueurs, les angles et l'historique sont conservés." else "Vidéos supprimées : le profil et les mesures restent enregistrés.")
            if (vBytes > 0) BtnOutline("Supprimer les vidéos (garder le profil)", { delVid = true })
        }
        if (delVid) AlertDialog(onDismissRequest = { delVid = false }, containerColor = Surface2,
            title = { Text("Supprimer les vidéos ?", color = TextHi) },
            text = { Text("Seuls les fichiers vidéo sont effacés. Le profil force-vitesse et les mesures de ce test sont conservés.", color = TextMid) },
            confirmButton = { TextButton({ vm.deleteVideos(sid); delVid = false }) { Text("Supprimer", color = RedBright) } },
            dismissButton = { TextButton({ delVid = false }) { Text("Annuler", color = TextHi) } })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RecoScreen(vm: AppVM, sid: String, onMenu: () -> Unit) {
    val session = vm.session(sid) ?: return
    val c = remember(session) { compute(session) }
    val p = c.profile
    PageV(vm, "Recommandations", onMenu) {
        if (p == null) { Card2 { Body("Calculez d'abord un profil valide.") }; return@PageV }
        val cat = p.category
        val plan = Reco.plan(cat)
        Card2 {
            Text(cat.label, color = RedBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Body("Profil à ${fmt(p.ratioPct, 0)} % de l'optimal · Pmax ${fmt(p.pmax, 1)} W/kg.", TextHi)
            Body(when {
                cat.isForceDeficit -> "Objectif : réduire le déséquilibre en développant en priorité la force maximale (F0), ce qui augmente aussi Pmax. Entraînement à charges élevées."
                cat.isVelocityDeficit -> "Objectif : réduire le déséquilibre en développant en priorité la vitesse d'extension (V0) : efforts balistiques à vitesse maximale, charges légères (< 30 % 1RM) ou négatives (assistées)."
                else -> "Objectif : augmenter Pmax en conservant le profil proche de l'optimal, en couvrant tout le spectre force-vitesse (charges lourdes, optimales et balistiques)."
            })
            RefTag(Refs.S12); RefTag(Refs.J17)
        }
        Card2 {
            H("Protocole d'entraînement (étude)")
            Body("• Durée : 9 semaines.\n• 2 séances par semaine, séparées de 48 h.\n• 6 exercices par semaine × 3 séries = 18 séries/semaine (3 exercices × 3 séries par séance).\n• Efforts maximaux (intention de vitesse maximale pour les exercices de puissance et de vitesse).\n• Pas d'autre musculation du bas du corps en parallèle ; activité sportive habituelle conservée.\n• Nouveau test F-v 1 semaine après la fin du cycle (et un test intermédiaire si le profil change de catégorie).")
            Body("Le nombre de répétitions n'est pas précisé dans l'étude ; la dose générique citée pour la force est 3–6 séries × 2–6 répétitions.", size = 13)
            RefTag(Refs.J17)
        }
        Card2 {
            H("Répartition hebdomadaire")
            plan.forEach { Body("• ${it.quality.label} : ${it.count} exercice(s) × 3 séries = ${it.count * 3} séries", TextHi) }
            RefTag(Refs.J17)
        }
        val (sa, sb) = Reco.sessions(cat)
        Card2 { H("Séance A"); sa.forEach { Body("• $it", TextHi) } }
        Card2 { H("Séance B"); sb.forEach { Body("• $it", TextHi) } }
        Expand("Exercices disponibles par qualité") {
            plan.forEach { s -> Text(s.quality.label, color = RedBright, fontWeight = FontWeight.Bold)
                s.quality.exercises.forEach { Body("• ${it.first} — ${it.second}", size = 13) } }
            Body("PC = poids de corps ; 1RM = répétition maximale. « Poussée horizontale » : extension des jambes sur chariot sans composante de gravité, pour surcharger la vitesse.", size = 12)
        }
        Card2 {
            H("Points d'attention")
            Body("• Dans l'étude, travailler la composante faible a fait baisser l'autre (V0 −19 % chez les déficitaires en force, F0 −6 % chez les déficitaires en vitesse) : conservez un peu de travail de la qualité forte pour maintenir Pmax.")
            Body("• Pour les 6 athlètes équilibrés, l'objectif est d'augmenter Pmax tout en gardant le profil près de 100 %.")
            Body("• Étude pilote (84 sportifs semi-professionnels de football et rugby, 9 semaines, durée fixe pour tous) : adaptez la durée en retestant, ces conseils ne remplacent pas l'avis d'un préparateur physique ou d'un médecin.")
            RefTag(Refs.J17)
        }
        BtnOutline("Retour aux résultats", { vm.pop() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
