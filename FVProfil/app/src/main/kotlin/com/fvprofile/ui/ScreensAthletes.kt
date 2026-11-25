@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fvprofile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fvprofile.core.*
import com.fvprofile.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val shortFmt = SimpleDateFormat("dd/MM", Locale.FRANCE)

@Composable
fun AthletesScreen(vm: AppVM, onMenu: () -> Unit) {
    PageV(vm, "Athlètes", onMenu, fab = {
        FloatingActionButton(onClick = { vm.push(Screen.AthleteEdit(null)) }, containerColor = Red, contentColor = Color.White) { Icon(Icons.Filled.Add, "Ajouter un athlète") }
    }) {
        if (vm.store.athletes.isEmpty()) Card2 {
            H("Bienvenue")
            Body("Créez un athlète, puis réalisez un test de squat jump chargé filmé avec le téléphone : l'application calcule son profil force-vitesse, le compare au profil optimal et propose un entraînement individualisé.")
            Body("Le menu ☰ (en haut à gauche) donne accès au protocole du test, au glossaire et aux références.")
            BtnPrimary("Créer un athlète", { vm.push(Screen.AthleteEdit(null)) }, icon = Icons.Filled.Add)
        }
        vm.store.athletes.forEach { a ->
            val n = vm.sessionsOf(a.id).size
            Card2(Modifier.clickable { vm.push(Screen.AthleteDetail(a.id)) }) {
                Text(a.name, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Body("${a.sport.ifBlank { "Sport non précisé" }} · ${fmt(a.heightCm, 0)} cm · ${fmt(a.massKg, 1)} kg")
                Body("$n test(s) enregistré(s)", size = 13)
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun AthleteEditScreen(vm: AppVM, id: String?, onMenu: () -> Unit) {
    val a = id?.let { vm.athlete(it) }
    var name by remember { mutableStateOf(a?.name ?: "") }
    var sex by remember { mutableStateOf(a?.sex ?: "H") }
    var birth by remember { mutableStateOf(a?.birth ?: "") }
    var sport by remember { mutableStateOf(a?.sport ?: "") }
    var height by remember { mutableStateOf(a?.heightCm?.let { fmt(it, 0) } ?: "") }
    var mass by remember { mutableStateOf(a?.massKg?.let { fmt(it, 1) } ?: "") }
    var leg by remember { mutableStateOf(a?.legLengthM?.let { fmt(it * 100, 1) } ?: "") }
    var start by remember { mutableStateOf(a?.startHeightM?.let { fmt(it * 100, 1) } ?: "") }
    var notes by remember { mutableStateOf(a?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val hPO = (parseD(leg) ?: 0.0) - (parseD(start) ?: 0.0)
    val valid = name.isNotBlank() && (parseD(height) ?: 0.0) > 100 && (parseD(mass) ?: 0.0) > 20 && (parseD(leg) ?: 0.0) > 50 && (parseD(start) ?: 0.0) > 20 && hPO > 10
    PageV(vm, if (a == null) "Nouvel athlète" else "Modifier l'athlète", onMenu) {
        Card2 {
            H("Identité")
            OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sexe", color = TextMid)
                listOf("H" to "Homme", "F" to "Femme").forEach { (k, l) -> FilterChip(sex == k, { sex = k }, { Text(l) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi)) }
            }
            OutlinedTextField(birth, { birth = it }, label = { Text("Date de naissance (JJ/MM/AAAA, facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(sport, { sport = it }, label = { Text("Sport / poste") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        }
        Card2 {
            H("Mesures")
            NumField("Taille", height, { height = it }, suffix = "cm")
            NumField("Poids", mass, { mass = it }, suffix = "kg")
            Body("Toute modification du poids est ajoutée au suivi de poids de l'athlète avec la date et l'heure du jour.", size = 13)
        }
        Card2 {
            H("Mesures pour la distance de poussée (hPO)")
            Body("Indispensables au calcul de la force et de la puissance. Faites-les avec un mètre, une fois pour toutes (à refaire si la position de départ change).")
            NumField("Longueur du membre inférieur étendu", leg, { leg = it }, suffix = "cm")
            Body("Du sommet de la crête iliaque (ou grand trochanter) à la pointe du pied, jambe tendue et pied en flexion plantaire maximale (position du décollage).", size = 13)
            NumField("Hauteur de départ (hS)", start, { start = it }, suffix = "cm")
            Body("Même repère osseux → sol, en position de départ du saut : genoux fléchis à ≈ 90°, vérifié à l'équerre/règle.", size = 13)
            Text("hPO = ${fmt(hPO, 1)} cm", color = if (hPO > 10) RedBright else Warn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            RefTag(Refs.S08)
        }
        Card2 {
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        }
        BtnPrimary("Enregistrer", {
            val newMass = parseD(mass)!!
            val aid = a?.id ?: vm.newId()
            vm.saveAthlete(Athlete(aid, name.trim(), sex, birth.trim(), sport.trim(), parseD(height)!!, newMass, parseD(leg)!! / 100, parseD(start)!! / 100, notes, a?.weights ?: emptyList()))
            if (a == null || a.massKg != newMass) vm.addWeight(aid, newMass)   // ajoute une pesée datée au suivi du poids
            vm.pop()
        }, Modifier.fillMaxWidth(), enabled = valid)
        if (!valid) Body("Renseignez toutes les mesures ; la longueur du membre doit être supérieure à la hauteur de départ.", Warn, 13)
        if (a != null) BtnOutline("Supprimer l'athlète", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Surface2,
        title = { Text("Supprimer ?", color = TextHi) }, text = { Text("L'athlète, ses tests et ses vidéos seront définitivement supprimés.", color = TextMid) },
        confirmButton = { TextButton({ vm.deleteAthlete(a!!.id); vm.goRoot(Screen.Athletes) }) { Text("Supprimer", color = RedBright) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuler", color = TextHi) } })
}

private enum class Metric(val label: String, val unit: String, val get: (Session, Computation) -> Double?) {
    PMAX("Pmax", "W/kg", { _, c -> c.profile?.pmax }),
    PABS("Pmax absolue", "W", { _, c -> c.profile?.pmaxAbs }),
    F0("F0", "N/kg", { _, c -> c.profile?.f0 }),
    V0("V0", "m/s", { _, c -> c.profile?.v0 }),
    RATIO("Profil / optimal", "%", { _, c -> c.profile?.ratioPct }),
    H("Saut 0 kg", "cm", { _, c -> c.hUnloaded?.times(100) }),
    R2("R²", "", { _, c -> c.diag.r2 }),
    MASS("Poids", "kg", { s, _ -> s.massKg })
}

private fun ticksFor(dates: List<Long>, xs: List<Double>): List<Pair<Double, String>> {
    val n = dates.size
    val idx = if (n <= 5) (0 until n).toList() else (0..4).map { it * (n - 1) / 4 }.distinct()
    return idx.map { xs[it] to shortFmt.format(Date(dates[it])) }
}

@Composable
fun AthleteDetailScreen(vm: AppVM, id: String, onMenu: () -> Unit) {
    val a = vm.athlete(id) ?: run { return }
    val sess = vm.sessionsOf(id)
    val comps = remember(sess) { sess.map { it to compute(it) } }
    var metric by remember { mutableStateOf(Metric.PMAX) }
    var delId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var weightDialog by remember { mutableStateOf(false) }
    var weightText by remember { mutableStateOf("") }
    var weightOpen by remember { mutableStateOf(false) }
    var delWeight by remember { mutableStateOf<Long?>(null) }
    val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi)
    val weights = a.weights.sortedBy { it.date }
    val done = comps.filter { it.second.profile != null }
    PageV(vm, a.name, onMenu, actions = { IconButton({ vm.push(Screen.AthleteEdit(id)) }) { Icon(Icons.Filled.Edit, "Modifier") } }) {
        Card2 {
            Body("${a.sport.ifBlank { "—" }} · ${fmt(a.heightCm, 0)} cm · ${fmt(a.massKg, 1)} kg", TextHi)
            Body("Membre inférieur ${fmt(a.legLengthM * 100, 1)} cm · hS ${fmt(a.startHeightM * 100, 1)} cm · hPO ${fmt(a.hPO * 100, 1)} cm · ${sess.size} test(s)", size = 13)
            BtnOutline("Suivi du poids · ${fmt(a.massKg, 1)} kg  ${if (weightOpen) "▲" else "▼"}", { weightOpen = !weightOpen }, Modifier.fillMaxWidth())
            if (weightOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Poids actuel", fmt(a.massKg, 1), "kg", Modifier.weight(1f), RedBright)
                if (weights.size >= 2) Stat("Depuis la 1ʳᵉ pesée", (if (a.massKg - weights.first().kg >= 0) "+" else "") + fmt(a.massKg - weights.first().kg, 1), "kg", Modifier.weight(1f))
            }
            if (weights.size >= 2) {
                val t0 = weights.first().date
                val xs = weights.map { (it.date - t0) / 86400000.0 }
                XYChart(listOf(Series(weights.mapIndexed { i, w -> xs[i] to w.kg }, RedBright, line = true)), "date de pesée", "poids (kg)", xTicks = ticksFor(weights.map { it.date }, xs))
            }
            BtnOutline("Mettre à jour le poids", { weightText = fmt(a.massKg, 1); weightDialog = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            weights.reversed().take(8).forEachIndexed { i, w ->
                val prev = weights.getOrNull(weights.size - 2 - i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDT(w.date), color = TextMid, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(fmt(w.kg, 1) + " kg" + (prev?.let { " (" + (if (w.kg - it.kg >= 0) "+" else "") + fmt(w.kg - it.kg, 1) + ")" } ?: ""), color = TextHi, fontSize = 13.sp)
                    if (weights.size > 1) IconButton({ delWeight = w.date }, Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, "Supprimer la pesée", tint = TextMid, modifier = Modifier.size(18.dp)) }
                }
            }
            Body("Le poids utilisé pour chaque test est celui saisi au moment du test ; il ne change pas si vous modifiez le poids plus tard.", size = 12)
            }
        }
        BtnPrimary("Nouveau test", { vm.push(Screen.Setup(id)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)

        // ---- Performances
        if (done.isNotEmpty()) {
            val last = done.last(); val prev = done.getOrNull(done.size - 2)
            fun delta(m: Metric): String {
                val x = m.get(last.first, last.second); val y = prev?.let { m.get(it.first, it.second) }
                return if (x != null && y != null) (if (x - y >= 0) "+" else "") + fmt(x - y, if (m == Metric.R2) 3 else 1) + " vs test précédent" else ""
            }
            Card2 {
                H("Performances — dernier test")
                Body("${sessionTitle(last.first)} · ${fmtDT(last.first.date)}", size = 13)
                val lp = last.second.profile!!
                listOf(listOf(Metric.PMAX to lp.pmax, Metric.PABS to lp.pmaxAbs), listOf(Metric.F0 to lp.f0, Metric.V0 to lp.v0), listOf(Metric.RATIO to lp.ratioPct, Metric.H to (last.second.hUnloaded?.times(100) ?: Double.NaN))).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (m, v) -> Column(Modifier.weight(1f)) {
                            Stat(m.label, fmt(v, if (m == Metric.V0) 2 else 1), m.unit, Modifier.fillMaxWidth())
                            Text(delta(m), color = TextMid, fontSize = 11.sp)
                        } }
                    }
                }
                val bestP = done.maxByOrNull { it.second.profile!!.pmax }!!
                val bestH = done.filter { it.second.hUnloaded != null }.maxByOrNull { it.second.hUnloaded!! }
                Body("Record Pmax : ${fmt(bestP.second.profile!!.pmax, 1)} W/kg (${fmtDT(bestP.first.date)})", TextHi, 13)
                if (bestH != null) Body("Record saut sans charge : ${fmt(bestH.second.hUnloaded!! * 100, 1)} cm (${fmtDT(bestH.first.date)})", TextHi, 13)
                Body("Profil actuel : ${lp.category.label} (${fmt(lp.ratioPct, 0)} % de l'optimal)", RedBright, 14)
            }
        }
        if (done.size >= 2) Card2 {
            H("Évolution des performances")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric.values().forEach { m -> FilterChip(metric == m, { metric = m }, { Text(m.label, fontSize = 13.sp) }, colors = chip) }
            }
            val pts = done.mapIndexedNotNull { i, d -> metric.get(d.first, d.second)?.let { i.toDouble() to it } }
            XYChart(listOf(Series(pts, RedBright, line = true)), "tests (date)", "${metric.label} ${metric.unit}".trim(),
                xTicks = done.mapIndexed { i, d -> i.toDouble() to shortFmt.format(Date(d.first.date)) })
            val first = metric.get(done.first().first, done.first().second); val lastV = metric.get(done.last().first, done.last().second)
            if (first != null && lastV != null) Body("Premier → dernier test : ${fmt(first)} → ${fmt(lastV)} ${metric.unit} (${if (lastV >= first) "+" else ""}${fmt(lastV - first)})")
            RefTag(Refs.M16)
        }

        // ---- Historique
        Text("Historique des tests", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (sess.isEmpty()) Body("Aucun test pour le moment.")
        comps.reversed().forEach { (s, c) ->
            Card2(Modifier.clickable { vm.push(if (c.profile != null) Screen.Results(s.id) else Screen.Hub(s.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sessionTitle(s), color = TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Body("${fmtDT(s.date)} · ${fmt(s.massKg, 1)} kg", size = 12)
                        val p = c.profile
                        if (p != null) Body("Pmax ${fmt(p.pmax, 1)} W/kg · F0 ${fmt(p.f0, 1)} · V0 ${fmt(p.v0)} · ${fmt(p.ratioPct, 0)} % de l'optimal · R² ${fmt(p.r2, 3)}", size = 13)
                        else Body("En cours / incomplet (${s.sets.size} charge(s))", Warn, 13)
                    }
                    IconButton({ renameId = s.id; renameText = s.name }) { Icon(Icons.Filled.Edit, "Renommer", tint = TextMid) }
                    IconButton({ delId = s.id }) { Icon(Icons.Filled.Delete, "Supprimer", tint = TextMid) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    delId?.let { d -> ConfirmDialog("Supprimer ce test ?", "Le test, ses mesures, son profil et ses vidéos seront supprimés.", "Supprimer", { vm.deleteSession(d) }, { delId = null }) }
    renameId?.let { r -> AlertDialog(onDismissRequest = { renameId = null }, containerColor = Surface2,
        title = { Text("Nom du test", color = TextHi) },
        text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Nom (vide = date et heure)") }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton({ vm.renameSession(r, renameText); renameId = null }) { Text("Valider", color = RedBright) } },
        dismissButton = { TextButton({ renameId = null }) { Text("Annuler", color = TextHi) } }) }
    delWeight?.let { d -> ConfirmDialog("Supprimer cette pesée ?", "La pesée du ${fmtDT(d)} sera retirée du suivi du poids.", "Supprimer", { vm.deleteWeight(id, d) }, { delWeight = null }) }
    if (weightDialog) AlertDialog(onDismissRequest = { weightDialog = false }, containerColor = Surface2,
        title = { Text("Nouvelle pesée", color = TextHi) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { NumField("Poids", weightText, { weightText = it }, suffix = "kg"); Body("Enregistrée avec la date et l'heure actuelles.", size = 13) } },
        confirmButton = { TextButton({ parseD(weightText)?.takeIf { it in 20.0..300.0 }?.let { vm.addWeight(id, it) }; weightDialog = false }) { Text("Ajouter", color = RedBright) } },
        dismissButton = { TextButton({ weightDialog = false }) { Text("Annuler", color = TextHi) } })
}
