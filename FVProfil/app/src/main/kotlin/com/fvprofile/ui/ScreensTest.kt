@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fvprofile

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fvprofile.camera.CamOption
import com.fvprofile.camera.CamOptions
import com.fvprofile.camera.CamRecorder
import com.fvprofile.core.*
import com.fvprofile.ui.*
import com.fvprofile.video.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun FormatPicker(options: List<CamOption>, selectedKey: String, onSelect: (CamOption) -> Unit) {
    if (options.isEmpty()) { Body("Aucun format détecté (caméra indisponible). Vous pourrez quand même importer des vidéos.", Warn); return }
    options.groupBy { it.camName }.forEach { (cam, list) ->
        if (options.map { it.camName }.distinct().size > 1) Text(cam, color = RedBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        list.groupBy { it.highSpeed }.forEach { (hs, l) ->
            Text(if (hs) "Ralenti (haute vitesse)" else "Vidéo normale", color = TextMid, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                l.forEach { o -> FilterChip(o.key == selectedKey, { onSelect(o) }, { Text("${o.w}×${o.h} · ${o.fps} ips" + if (o.confirmed) "" else " ?", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi)) }
            }
        }
    }
}

@Composable
fun OrientationPicker(vm: AppVM) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Filmer en", color = TextMid, fontSize = 14.sp)
        listOf(false to "Paysage", true to "Portrait").forEach { (p, l) ->
            FilterChip(vm.portrait == p, { vm.setPortrait(p) }, { Text(l) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi))
        }
    }
}

@Composable
fun SetupScreen(vm: AppVM, athleteId: String, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val a = vm.athlete(athleteId) ?: return
    var showAll by remember { mutableStateOf(false) }
    val options = remember(showAll) { CamOptions.list(ctx, showAll) }
    var mass by remember { mutableStateOf(fmt(a.massKg, 1)) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (vm.camKey.isEmpty() || options.none { it.key == vm.camKey })
            (options.firstOrNull { it.fps in 100..130 && it.h >= 1080 } ?: options.firstOrNull { it.fps >= 100 } ?: options.firstOrNull())?.let { vm.setCam(it.key) }
    }
    PageV(vm, "Nouveau test — ${a.name}", onMenu) {
        Card2 {
            H("Nom du test")
            OutlinedTextField(name, { name = it }, label = { Text("Nom (facultatif, ex. « Pré-saison »)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Body("La date et l'heure du test sont enregistrées automatiquement.", size = 13)
        }
        Card2 {
            H("Poids du jour")
            NumField("Poids de corps (sans charge)", mass, { mass = it }, suffix = "kg")
            Body("Si le poids a changé, il est ajouté au suivi de poids de l'athlète (dernier poids connu : ${fmt(a.massKg, 1)} kg).", size = 13)
        }
        Card2 {
            H("Vidéo")
            OrientationPicker(vm)
            Body("Plus la cadence est élevée, plus la mesure est précise : 120 ips ou plus recommandé (±1 image = ±3,4 % sur la hauteur à 120 ips, ±13 % à 30 ips).")
            val cur = options.firstOrNull { it.key == vm.camKey }
            if (cur != null) Text("Format : ${cur.label}", color = RedBright, fontWeight = FontWeight.Bold)
            FormatPicker(options, vm.camKey) { vm.setCam(it.key) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(showAll, { showAll = it }, colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White, uncheckedTrackColor = Surface2, uncheckedThumbColor = TextMid))
                Spacer(Modifier.width(10.dp))
                Text("Afficher aussi les modes non confirmés (?)", color = TextHi, fontSize = 14.sp)
            }
        }
        Expand("Comprendre ces formats vidéo") {
            Body("• Chaque bouton = largeur × hauteur · images par seconde (ips). Plus d'ips = temps de vol plus précis.", size = 13)
            Body("• « Vidéo normale » : enregistrement continu à 24, 30, 60 ips… « Ralenti (haute vitesse) » : mode spécial 120 à 960 ips de la caméra.", size = 13)
            Body("• Android ne transmet aux applications que les modes que le constructeur DÉCLARE (API Camera2) et que le codeur vidéo accepte. L'application caméra du téléphone peut utiliser des modes propres au constructeur qui ne sont pas déclarés : ils n'apparaissent alors pas ici.", size = 13)
            Body("• L'option « modes non confirmés » propose, pour chaque taille, des cadences plausibles (24/30/60/120 ips) non déclarées : essayez-les ; si le téléphone refuse, un message d'erreur s'affiche à l'ouverture de la caméra.", size = 13)
            var diag by remember { mutableStateOf(false) }
            BtnOutline(if (diag) "Masquer le diagnostic" else "Afficher le diagnostic technique", { diag = !diag })
            if (diag) Text(CamOptions.diagnostic(ctx), color = TextMid, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Body("Les charges s'ajoutent une par une à l'étape suivante. Pensez à relire le protocole (menu ☰).", size = 13)
        BtnPrimary("Commencer le test", {
            val m = parseD(mass) ?: a.massKg
            val now = System.currentTimeMillis()
            if (m != a.massKg) vm.addWeight(a.id, m, now)
            val s = Session(vm.newId(), a.id, now, m, a.hPO, false, options.firstOrNull { it.key == vm.camKey }?.label ?: "Import", emptyList(), name.trim())
            vm.saveSession(s); vm.replace(Screen.Hub(s.id))
        }, Modifier.fillMaxWidth(), enabled = (parseD(mass) ?: 0.0) > 20)
    }
}

@Composable
fun SetCard(done: Boolean, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (done) Color(0xFF2A1215) else Surface1),
        border = BorderStroke(1.dp, if (done) Red.copy(alpha = 0.75f) else Outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun HubScreen(vm: AppVM, sid: String, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = vm.session(sid) ?: return
    val a = vm.athlete(session.athleteId)
    val options = remember { CamOptions.list(ctx, true) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var editSet by remember { mutableStateOf<LoadSet?>(null) }
    var editText by remember { mutableStateOf("") }
    var delSet by remember { mutableStateOf<LoadSet?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var fmtDialog by remember { mutableStateOf(false) }
    var delVideos by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pend = pendingImport
        if (uri != null && pend != null) scope.launch {
            busy = true
            val res = withContext(Dispatchers.IO) {
                try {
                    val dir = File(ctx.filesDir, "videos").apply { mkdirs() }
                    val f = File(dir, "${pend.first}_${System.currentTimeMillis()}.mp4")
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    val vs = VideoSource(f.absolutePath); val fps = vs.fpsDetected; vs.close(); f.absolutePath to fps
                } catch (e: Exception) { null }
            }
            busy = false
            if (res != null) { vm.attachVideo(sid, pend.first, pend.second, res.first, res.second); vm.push(Screen.Analysis(sid, pend.first, pend.second)) }
        }
    }
    fun measured(s: LoadSet) = s.trials.count { it.tA(s.fpsFor(it)) != null }
    fun heights(s: LoadSet) = s.trials.mapNotNull { t -> t.tA(s.fpsFor(t))?.let { fmt(Mech.heightFromFlight(it) * 100, 1) + " cm" } }.joinToString(" / ")
    val ready = session.sets.count { measured(it) >= 1 }
    val videoBytes = vm.videoBytes(sid)
    val prevSession = vm.sessionsOf(session.athleteId).filter { it.id != sid && it.sets.isNotEmpty() && it.date < session.date }.lastOrNull()
    PageV(vm, sessionTitle(session), onMenu) {
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sessionTitle(session), color = TextHi, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Body("${a?.name ?: ""} · réalisé le ${fmtDT(session.date)}", size = 13)
                }
                IconButton({ renameText = session.name; renameDialog = true }) { Icon(Icons.Filled.Edit, "Renommer le test", tint = TextMid) }
            }
            Body("Masse ${fmt(session.massKg, 1)} kg · hPO ${fmt(session.hPO * 100, 1)} cm", size = 13)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Body("${options.firstOrNull { it.key == vm.camKey }?.label ?: "—"} · ${if (vm.portrait) "portrait" else "paysage"}", size = 13)
                Spacer(Modifier.width(8.dp))
                TextButton({ fmtDialog = true }) { Text("Changer", color = RedBright) }
            }
        }
        // Guide : prochaine étape
        val firstNoVideo = session.sets.firstOrNull { it.videoPath == null && measured(it) == 0 }
        val firstUnanalysed = session.sets.firstOrNull { it.videoPath != null && measured(it) == 0 }
        val firstOne = session.sets.firstOrNull { measured(it) == 1 }
        val analysedCount = session.sets.count { measured(it) >= 2 }
        val next = when {
            session.sets.isEmpty() -> "Ajoutez la première charge (en général 0 kg)."
            firstNoVideo != null -> "Filmez ou importez la vidéo de la charge ${fmt(firstNoVideo.loadKg, 1)} kg."
            firstUnanalysed != null -> "Analysez la vidéo de la charge ${fmt(firstUnanalysed.loadKg, 1)} kg : bouton « Analyser »."
            firstOne != null -> "Charge ${fmt(firstOne.loadKg, 1)} kg : un seul essai mesuré. Marquez aussi le 2ᵉ essai, ou filmez/importez une 2ᵉ vidéo."
            analysedCount < 5 -> "Ajoutez une charge plus lourde (5 charges recommandées, la dernière ≈ 10 cm de saut)."
            else -> "Toutes les charges sont analysées : calculez le profil."
        }
        Card2 { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("➜", color = RedBright, fontSize = 20.sp); Spacer(Modifier.width(10.dp))
            Column { Text("Prochaine étape", color = RedBright, fontSize = 12.sp, fontWeight = FontWeight.Bold); Body(next, TextHi) }
        } }
        if (busy) Card2 { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = RedBright, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Body("Import de la vidéo…") } }
        session.sets.forEach { s ->
            val n = measured(s)
            val done = n >= 2
            val open = expanded[s.id] ?: !done
            val n1 = s.trials.count { it.src == 0 && it.tA(s.fps) != null }
            val needSecond = s.videoPath != null && s.video2Path == null && n1 == 1
            val hasSecond = s.video2Path != null
            if (!open) SetCard(true, { expanded[s.id] = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = RedBright, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text("Charge ${fmt(s.loadKg, 1)} kg", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(heights(s), color = TextMid, fontSize = 13.sp); Spacer(Modifier.width(8.dp)); Text("▼", color = RedBright)
                }
            } else SetCard(done, null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) { Icon(Icons.Filled.Check, null, tint = RedBright, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                    Text("Charge ${fmt(s.loadKg, 1)} kg", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ editSet = s; editText = fmt(s.loadKg, 1) }) { Icon(Icons.Filled.Edit, "Modifier la charge", tint = TextMid) }
                    IconButton({ delSet = s }) { Icon(Icons.Filled.Delete, "Retirer", tint = TextMid) }
                    if (done) TextButton({ expanded[s.id] = false }) { Text("▲", color = RedBright) }
                }
                val st = when {
                    s.videoPath == null && n == 0 -> "Vidéo à réaliser"
                    s.videoPath == null -> "$n saut(s) mesuré(s) : ${heights(s)} · vidéo supprimée (mesures conservées)"
                    n == 0 -> "Vidéo prête · ${fmt(s.fps, 0)} ips · à analyser"
                    else -> "${fmt(s.fps, 0)} ips · $n saut(s) mesuré(s) : ${heights(s)}"
                }
                Body(st, if (done) TextHi else TextMid)
                if (hasSecond || needSecond) Text("Vidéo 1", color = RedBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BtnPrimary(if (s.videoPath == null && n == 0) "Filmer" else "Refilmer", { vm.push(Screen.Record(sid, s.id, 0)) }, Modifier.weight(1f), enabled = options.any { it.key == vm.camKey }, icon = Icons.Filled.Videocam)
                    BtnOutline("Importer", { pendingImport = s.id to 0; importer.launch("video/*") }, Modifier.weight(1f), icon = Icons.Filled.FolderOpen)
                }
                if (s.videoPath != null) BtnOutline(if (n1 > 0) "Revoir les marqueurs" else "Analyser la vidéo", { vm.push(Screen.Analysis(sid, s.id, 0)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
                if (s.videoPath == null && n == 0) Body("Les 2 essais peuvent être filmés dans la même vidéo, ou un seul essai : une 2ᵉ vidéo vous sera alors proposée.", size = 12)
                if (needSecond || hasSecond) {
                    Text("Vidéo 2 — essai manquant", color = RedBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BtnPrimary(if (hasSecond) "Refilmer" else "Filmer", { vm.push(Screen.Record(sid, s.id, 1)) }, Modifier.weight(1f), enabled = options.any { it.key == vm.camKey }, icon = Icons.Filled.Videocam)
                        BtnOutline("Importer", { pendingImport = s.id to 1; importer.launch("video/*") }, Modifier.weight(1f), icon = Icons.Filled.FolderOpen)
                    }
                    if (hasSecond) BtnOutline("Analyser / revoir la vidéo 2", { vm.push(Screen.Analysis(sid, s.id, 1)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
                }
            }
        }
        BtnOutline(if (session.sets.isEmpty()) "Ajouter la première charge" else "Ajouter une charge", {
            val m = session.massKg
            val base = prevSession?.sets?.map { it.loadKg }?.distinct()?.sorted() ?: listOf(0.0, 0.25, 0.5, 0.75, 1.0).map { (m * it / 5).roundToInt() * 5.0 }.distinct()
            addText = base.firstOrNull { c -> session.sets.none { it.loadKg == c } }?.let { fmt(it, 1) } ?: ""
            addDialog = true
        }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        val analysed = session.sets.count { measured(it) >= 2 }
        val total = max(5, session.sets.size)
        val canCompute = ready >= 3
        val allGood = session.sets.size >= 5 && analysed == session.sets.size
        val label = "Calculer le profil force-vitesse ($analysed/$total charges analysées)"
        if (allGood) BtnPrimary(label, { vm.push(Screen.Results(sid)) }, Modifier.fillMaxWidth().heightIn(min = 56.dp))
        else if (canCompute) BtnOutline(label, { vm.push(Screen.Results(sid)) }, Modifier.fillMaxWidth().heightIn(min = 56.dp))
        else BtnPrimary(label, {}, Modifier.fillMaxWidth(), enabled = false)
        Body(when {
            allGood -> "Toutes les charges sont analysées : profil complet possible."
            canCompute -> "Le profil peut être tracé dès maintenant, mais il sera plus fiable avec 5 charges ou plus, chacune analysée avec ses 2 sauts."
            else -> "Il faut au moins 3 charges mesurées pour tracer le profil (5 recommandées) : actuellement $ready."
        }, if (allGood) Ok else TextMid, 13)
        if (videoBytes > 0) TextButton({ delVideos = true }) { Text("Supprimer les vidéos (${fmt(videoBytes / 1e6, 0)} Mo) — mesures et profil conservés", color = RedBright, fontSize = 13.sp) }
        Spacer(Modifier.height(24.dp))
    }
    if (renameDialog) AlertDialog(onDismissRequest = { renameDialog = false }, containerColor = Surface2,
        title = { Text("Nom du test", color = TextHi) },
        text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Nom (vide = date et heure)") }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton({ vm.renameSession(sid, renameText); renameDialog = false }) { Text("Valider", color = RedBright) } },
        dismissButton = { TextButton({ renameDialog = false }) { Text("Annuler", color = TextHi) } })
    if (addDialog) {
        val m = session.massKg
        val fromPrev = prevSession != null
        val base = prevSession?.sets?.map { it.loadKg }?.distinct()?.sorted() ?: listOf(0.0, 0.25, 0.5, 0.75, 1.0).map { (m * it / 5).roundToInt() * 5.0 }.distinct()
        val sugg = base.filter { c -> session.sets.none { it.loadKg == c } }
        AlertDialog(onDismissRequest = { addDialog = false }, containerColor = Surface2,
            title = { Text("Nouvelle charge", color = TextHi) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Charge additionnelle", addText, { addText = it }, suffix = "kg")
                if (sugg.isNotEmpty()) {
                    Body(if (fromPrev) "Charges du dernier test (${fmtDT(prevSession!!.date)}) :" else "Suggestions (0 → 100 % du poids de corps) :", size = 13)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sugg.forEach { c -> AssistChip({ addText = fmt(c, 1) }, { Text("${fmt(c, 1)} kg", color = TextHi) }) } }
                }
                Body("Objectif : la dernière charge doit permettre ≈ 10 cm de saut.", size = 12)
            } },
            confirmButton = { Row {
                if (sugg.size > 1) TextButton({
                    vm.saveSession(session.copy(sets = session.sets + sugg.map { LoadSet(vm.newId(), it) })); addDialog = false
                }) { Text("Tout ajouter", color = TextHi) }
                TextButton({
                    parseD(addText)?.let { v -> val ns = LoadSet(vm.newId(), v); vm.saveSession(session.copy(sets = session.sets + ns)); expanded[ns.id] = true }
                    addDialog = false }) { Text("Ajouter", color = RedBright) }
            } },
            dismissButton = { TextButton({ addDialog = false }) { Text("Annuler", color = TextHi) } })
    }
    editSet?.let { s -> AlertDialog(onDismissRequest = { editSet = null }, containerColor = Surface2,
        title = { Text("Modifier la charge", color = TextHi) },
        text = { NumField("Charge", editText, { editText = it }, suffix = "kg") },
        confirmButton = { TextButton({ parseD(editText)?.let { v -> vm.updateSet(sid, s.id) { it.copy(loadKg = v) } }; editSet = null }) { Text("Valider", color = RedBright) } },
        dismissButton = { TextButton({ editSet = null }) { Text("Annuler", color = TextHi) } }) }
    delSet?.let { s -> ConfirmDialog("Retirer cette charge ?", "La charge ${fmt(s.loadKg, 1)} kg, ses vidéos et ses marqueurs seront supprimés.", "Retirer",
        { vm.deleteSetFiles(s); vm.saveSession(session.copy(sets = session.sets.filter { it.id != s.id })) }, { delSet = null }) }
    if (fmtDialog) AlertDialog(onDismissRequest = { fmtDialog = false }, containerColor = Surface2,
        title = { Text("Vidéo", color = TextHi) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OrientationPicker(vm); FormatPicker(options, vm.camKey) { vm.setCam(it.key) } } },
        confirmButton = { TextButton({ fmtDialog = false }) { Text("OK", color = RedBright) } })
    if (delVideos) ConfirmDialog("Supprimer les vidéos ?", "Les marqueurs (décollage, réception, angles) et le profil restent enregistrés ; seules les vidéos sont effacées (plus de relecture image par image).",
        "Supprimer", { vm.deleteVideos(sid) }, { delVideos = false })
}

@Composable
fun RecordScreen(vm: AppVM, sid: String, setId: String, videoIdx: Int) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val scope = rememberCoroutineScope()
    val session = vm.session(sid) ?: return
    val set = session.sets.firstOrNull { it.id == setId } ?: return
    val opt = remember { CamOptions.list(ctx, true).firstOrNull { it.key == vm.camKey } }
    val portrait = vm.portrait
    val expInfo = remember { opt?.let { CamOptions.exposureInfo(ctx, it.cameraId) } ?: Triple(0, 0, 0f) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) {
        act.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    var rec by remember { mutableStateOf<CamRecorder?>(null) }
    var ready by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { rec?.release() } }
    LaunchedEffect(recording) { if (recording) { seconds = 0; while (true) { delay(1000); seconds++ } } }

    fun finish(r: CamRecorder) {
        if (finished) return
        finished = true; recording = false
        val ok = r.stopAndRelease()
        if (ok) scope.launch {
            val fps = withContext(Dispatchers.IO) { try { VideoSource(r.outFile.absolutePath).let { v -> val f = v.fpsDetected; v.close(); f } } catch (e: Exception) { opt?.fps?.toDouble() ?: 30.0 } }
            vm.attachVideo(sid, setId, videoIdx, r.outFile.absolutePath, fps)
            vm.replace(Screen.Analysis(sid, setId, videoIdx))   // on enchaîne directement sur l'analyse
        } else { finished = false; vm.pop() }
    }

    @Composable fun Preview() {
        if (opt != null && granted) AndroidView(factory = { c ->
            var opened = false
            SurfaceView(c).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) { h.setFixedSize(opt.w, opt.h) }
                    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {
                        if (opened || w != opt.w || hh != opt.h) return
                        opened = true
                        val dir = File(ctx.filesDir, "videos").apply { mkdirs() }
                        val r = CamRecorder(ctx, opt, File(dir, "${setId}_${System.currentTimeMillis()}.mp4"))
                        r.onSystemStop = { finish(r) }
                        rec = r
                        @Suppress("DEPRECATION") val deg = act.windowManager.defaultDisplay.rotation * 90
                        r.open(h.surface, deg, vm.exposure, { ready = true }, { error = it })
                    }
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
            }
        }, modifier = Modifier.aspectRatio(if (portrait) opt.h.toFloat() / opt.w else opt.w.toFloat() / opt.h))
        else Text(if (!granted) "Autorisation caméra requise." else "Format vidéo indisponible : choisissez-en un autre.", color = TextHi, modifier = Modifier.padding(20.dp))
    }
    @Composable fun Texts() {
        val what = if (videoIdx == 0) "Charge ${fmt(set.loadKg, 1)} kg" else "Charge ${fmt(set.loadKg, 1)} kg — vidéo 2"
        Text(what, color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Body(if (videoIdx == 0) "Filmez 1 ou 2 sauts : position de départ tenue ≈ 2 s, saut maximal, réception, repos, 2ᵉ saut éventuel." else "Filmez l'essai manquant : départ tenu ≈ 2 s, puis saut maximal.", size = 13)
        Body("${opt?.label ?: ""} · ${if (portrait) "portrait" else "paysage"}. Aperçu = rendu final, aucune limite de durée imposée.", size = 12)
        error?.let { Text(it, color = Warn, fontSize = 13.sp) }
    }
    @Composable fun Exposure() {
        if (expInfo.first != expInfo.second) {
            var v by remember { mutableFloatStateOf(vm.exposure.toFloat()) }
            Text("Luminosité (exposition) : ${if (v * expInfo.third >= 0) "+" else ""}${fmt(v * expInfo.third, 1)} EV", color = TextHi, fontSize = 13.sp)
            Slider(v, { v = it; vm.setExposure(it.toInt()); rec?.setExposure(it.toInt()) }, valueRange = expInfo.first.toFloat()..expInfo.second.toFloat(),
                colors = SliderDefaults.colors(thumbColor = RedBright, activeTrackColor = Red, inactiveTrackColor = Outline))
            Text("Le ralenti est souvent sombre : montez l'exposition et éclairez bien la scène.", color = TextMid, fontSize = 11.sp)
        } else Text("Réglage d'exposition non disponible sur ce mode : éclairez bien la scène (le ralenti est souvent sombre).", color = TextMid, fontSize = 11.sp)
    }
    @Composable fun RecButton() {
        Box(Modifier.size(76.dp).background(if (ready || recording) Red else Surface2, CircleShape).clickable(enabled = ready || recording) {
            val r = rec ?: return@clickable
            if (!recording) { if (r.start()) recording = true else error = "Démarrage impossible" } else finish(r)
        }, contentAlignment = Alignment.Center) {
            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, if (recording) "Arrêter" else "Enregistrer", tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
    val status = if (recording) "Appuyez pour arrêter" else if (ready) "Appuyez pour filmer" else "Ouverture caméra…"

    if (portrait) Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Preview() }
        Column(Modifier.fillMaxWidth().background(Surface1).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texts(); Exposure()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RecButton()
                Column(Modifier.weight(1f)) {
                    if (recording) Text("● %d:%02d".format(seconds / 60, seconds % 60), color = RedBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Body(status, size = 13)
                }
                TextButton({ vm.pop() }) { Text("Annuler", color = TextHi) }
            }
        }
    } else Row(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Preview() }
        Column(Modifier.width(230.dp).fillMaxHeight().background(Surface1).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Texts(); Exposure()
            if (recording) Text("● %d:%02d".format(seconds / 60, seconds % 60), color = RedBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            RecButton(); Body(status, size = 12)
            TextButton({ vm.pop() }) { Text("Annuler", color = TextHi) }
        }
    }
}
