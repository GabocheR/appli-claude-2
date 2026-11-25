@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fvprofile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fvprofile.core.*
import com.fvprofile.ui.*
import com.fvprofile.video.PoseTool
import com.fvprofile.video.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val markNames = listOf("Départ", "Décollage", "Réception")
private val ptNames = listOf("Épaule", "Hanche", "Genou", "Cheville")
private val ptColors = listOf(Color(0xFFFFB74D), Color(0xFFFF5252), Color(0xFF64B5F6), Color(0xFF81C784))

/** Zoom / déplacement / luminosité d'affichage, partagés entre la vue normale et le plein écran. */
class ZoomState {
    var z by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    var bright by mutableIntStateOf(0)
    fun reset() { z = 1f; tx = 0f; ty = 0f }
}

@Composable
fun FrameView(bmp: Bitmap?, zs: ZoomState, pts: List<Offset?>, active: Int, measuring: Boolean, isFull: Boolean,
              modifier: Modifier, onToggleFull: () -> Unit, onSelectPoint: (Int) -> Unit, onMovePoint: (Int, Offset) -> Unit, onTap: (Offset) -> Unit) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val ptsNow by rememberUpdatedState(pts)
    fun base(): Triple<Float, Float, Float>? {
        val b = bmp ?: return null
        if (box.width == 0 || box.height == 0) return null
        val s = min(box.width.toFloat() / b.width, box.height.toFloat() / b.height)
        return Triple(s, (box.width - b.width * s) / 2f, (box.height - b.height * s) / 2f)
    }
    fun toScreen(o: Offset): Offset {
        val g = base() ?: return o
        val cx = box.width / 2f; val cy = box.height / 2f
        val px = o.x * g.first + g.second; val py = o.y * g.first + g.third
        return Offset(cx + (px - cx) * zs.z + zs.tx, cy + (py - cy) * zs.z + zs.ty)
    }
    fun toBitmap(q: Offset): Offset? {
        val g = base() ?: return null
        val cx = box.width / 2f; val cy = box.height / 2f
        val px = cx + (q.x - cx - zs.tx) / zs.z; val py = cy + (q.y - cy - zs.ty) / zs.z
        return Offset((px - g.second) / g.first, (py - g.third) / g.first)
    }
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val nz = (zs.z * zoom).coerceIn(1f, 10f); val k = nz / zs.z
        val cx = box.width / 2f; val cy = box.height / 2f
        val ntx = (centroid.x - cx) - k * (centroid.x - cx - zs.tx) + pan.x
        val nty = (centroid.y - cy) - k * (centroid.y - cy - zs.ty) + pan.y
        val mx = (nz - 1f) * box.width / 2f; val my = (nz - 1f) * box.height / 2f
        zs.z = nz; zs.tx = ntx.coerceIn(-mx, mx); zs.ty = nty.coerceIn(-my, my)
    }
    fun nearest(o: Offset): Int? {
        var best = 70f; var idx: Int? = null
        ptsNow.forEachIndexed { i, p -> if (p != null) { val sp = toScreen(p); val d = hypot(sp.x - o.x, sp.y - o.y); if (d < best) { best = d; idx = i } } }
        return idx
    }
    val cf = if (zs.bright == 0) null else {
        val m = 1f + 0.35f * zs.bright; val o = 10f * zs.bright
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(m, 0f, 0f, 0f, o, 0f, m, 0f, 0f, o, 0f, 0f, m, 0f, o, 0f, 0f, 0f, 1f, 0f)))
    }
    Box(modifier.background(Color.Black).clipToBounds().onSizeChanged { box = it }
        .pointerInput(measuring, bmp) {
            // Un seul détecteur : pincement/déplacement (2 doigts), glisser un point, toucher pour placer/sélectionner
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dragIdx: Int? = if (measuring) nearest(down.position) else null
                dragIdx?.let { onSelectPoint(it) }
                var moved = false
                do {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        dragIdx = null; moved = true
                        transform(ev.calculateCentroid(), ev.calculatePan(), ev.calculateZoom())
                        ev.changes.forEach { it.consume() }
                    } else if (pressed.size == 1) {
                        val c = pressed[0]
                        if (!moved && hypot(c.position.x - down.position.x, c.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val di = dragIdx
                            if (di != null) toBitmap(c.position)?.let { onMovePoint(di, it) }
                            else transform(c.position, ev.calculatePan(), 1f)
                            c.consume()
                        }
                    }
                } while (ev.changes.any { it.pressed })
                if (!moved && measuring && dragIdx == null) toBitmap(down.position)?.let { onTap(it) }
            }
        }) {
        bmp?.let { b ->
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize().graphicsLayer { scaleX = zs.z; scaleY = zs.z; translationX = zs.tx; translationY = zs.ty },
                contentScale = ContentScale.Fit, colorFilter = cf)
        } ?: CircularProgressIndicator(color = RedBright, modifier = Modifier.align(Alignment.Center))
        if (measuring) Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until pts.size - 1) { val p = pts[i]; val q = pts[i + 1]; if (p != null && q != null) drawLine(Color.White.copy(alpha = 0.8f), toScreen(p), toScreen(q), 3f) }
            pts.forEachIndexed { i, p ->
                if (p != null) { val s = toScreen(p)
                    drawCircle(ptColors[i], if (i == active) 20f else 14f, s, style = Stroke(if (i == active) 4f else 3f))
                    drawCircle(ptColors[i], 3f, s) }
            }
        }
        val chipBg = Color(0x99000000)
        Text("×${"%.1f".format(zs.z)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(chipBg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).background(chipBg, RoundedCornerShape(8.dp)).clickable { onToggleFull() }.padding(6.dp)) {
            Icon(if (isFull) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (isFull) "Quitter le plein écran" else "Plein écran", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("−" to { transform(Offset(box.width / 2f, box.height / 2f), Offset.Zero, 1f / 1.5f) }, "+" to { transform(Offset(box.width / 2f, box.height / 2f), Offset.Zero, 1.5f) }, "1×" to { zs.reset() }).forEach { (l, f) ->
                Box(Modifier.background(chipBg, RoundedCornerShape(8.dp)).clickable { f() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(l, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("☀−" to { zs.bright = max(0, zs.bright - 1) }, "☀+" to { zs.bright = min(8, zs.bright + 1) }).forEach { (l, f) ->
                Box(Modifier.background(chipBg, RoundedCornerShape(8.dp)).clickable { f() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(l, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AnalysisScreen(vm: AppVM, sid: String, setId: String, videoIdx: Int, onMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session = vm.session(sid) ?: return
    val set0 = session.sets.firstOrNull { it.id == setId } ?: return
    val path = if (videoIdx == 0) set0.videoPath else set0.video2Path
    val fps0 = if (videoIdx == 0) set0.fps else set0.fps2
    val title = "Analyse — ${fmt(set0.loadKg, 1)} kg" + if (videoIdx == 1) " (vidéo 2)" else ""
    if (path == null) {
        PageV(vm, title, onMenu) {
            Card2 {
                H("Vidéo supprimée")
                Body("Les marqueurs et le profil sont conservés ; seule la relecture image par image n'est plus possible.")
                set0.trials.forEachIndexed { t, d ->
                    val tA = d.tA(set0.fpsFor(d))
                    Body("Essai ${t + 1} : " + (if (tA != null) "tA ${fmt(tA, 3)} s → ${fmt(Mech.heightFromFlight(tA) * 100, 1)} cm" else "non mesuré") +
                        " · genou ${d.kneeDeg?.let { fmt(it, 0) + "°" } ?: "—"} · hanche ${d.hipDeg?.let { fmt(it, 0) + "°" } ?: "—"}", TextHi)
                }
                BtnPrimary("Refilmer cette charge", { vm.replace(Screen.Record(sid, setId, videoIdx)) })
            }
        }
        return
    }
    val holder = remember { arrayOfNulls<VideoSource>(1) }
    val zs = remember { ZoomState() }
    val relevant = remember { set0.trials.indices.filter { set0.trials[it].src == videoIdx }.ifEmpty { listOf(1) } }
    val slots = remember { relevant.flatMap { t -> (0..2).map { k -> t to k } } }
    var src by remember { mutableStateOf<VideoSource?>(null) }
    var fps by remember { mutableDoubleStateOf(fps0) }
    var frame by remember { mutableIntStateOf(0) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var trials by remember { mutableStateOf(set0.trials) }
    fun markOf(d: TrialData, k: Int): Int? = when (k) { 0 -> d.startFrame; 1 -> d.takeoff; else -> d.landing }
    var target by remember { mutableIntStateOf(slots.indexOfFirst { (t, k) -> markOf(set0.trials[t], k) == null }.let { if (it < 0) 1 else it }) }
    var editing by remember { mutableStateOf(false) }
    var measuring by remember { mutableStateOf<Int?>(null) }
    var pts by remember { mutableStateOf<List<Offset?>>(listOf(null, null, null, null)) }
    var activePt by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    var fpsDialog by remember { mutableStateOf(false) }
    var fpsText by remember { mutableStateOf("") }
    val pose = remember { PoseTool() }

    DisposableEffect(Unit) { onDispose { holder[0]?.close(); pose.close() } }
    LaunchedEffect(path) {
        val s = withContext(Dispatchers.IO) { runCatching { VideoSource(path) }.getOrNull() }
        holder[0] = s; src = s
        if (s != null && fps <= 0) fps = s.fpsDetected
        if (s == null) info = "Impossible d'ouvrir la vidéo."
        else {
            val m = slots.map { (t, k) -> markOf(trials[t], k) }
            m.getOrNull(target)?.let { frame = it } ?: m.filterNotNull().firstOrNull()?.let { frame = max(0, it - 2) }
        }
    }
    LaunchedEffect(src, frame) {
        val s = src ?: return@LaunchedEffect
        delay(30)
        val b = withContext(Dispatchers.IO) { s.frame(frame, 1280) }
        if (b != null) bmp = b
    }
    // Les marqueurs sont enregistrés en continu et conservés
    LaunchedEffect(trials, fps) { vm.updateSet(sid, setId) { if (videoIdx == 0) it.copy(trials = trials, fps = fps) else it.copy(trials = trials, fps2 = fps) } }

    fun getMark(t: Int, k: Int): Int? = markOf(trials[t], k)
    fun setMark(t: Int, k: Int, v: Int) {
        trials = trials.toMutableList().also { l -> val d = l[t]; l[t] = when (k) { 0 -> d.copy(startFrame = v); 1 -> d.copy(takeoff = v); else -> d.copy(landing = v) } }
    }
    fun markHere() {
        val (t, k) = slots[target]
        setMark(t, k, frame)
        val next = (1..slots.size).map { (target + it) % slots.size }.firstOrNull { getMark(slots[it].first, slots[it].second) == null }
        if (next != null) target = next
    }
    fun ang(a: Offset?, v: Offset?, b: Offset?): Double? =
        if (a == null || v == null || b == null) null else angleAt(P(a.x.toDouble(), a.y.toDouble()), P(v.x.toDouble(), v.y.toDouble()), P(b.x.toDouble(), b.y.toDouble()))
    val kneeNow = ang(pts[1], pts[2], pts[3])
    val hipNow = ang(pts[0], pts[1], pts[2])
    fun placePoint(o: Offset) { pts = pts.toMutableList().also { it[activePt] = o } }   // jamais de passage automatique au point suivant
    fun movePoint(i: Int, o: Offset) { pts = pts.toMutableList().also { it[i] = o } }
    fun validateAngles() {
        val t = measuring ?: return
        trials = trials.toMutableList().also { l -> l[t] = l[t].copy(startFrame = frame, kneeDeg = kneeNow ?: l[t].kneeDeg, hipDeg = hipNow ?: l[t].hipDeg) }
        measuring = null
    }
    fun detectAI() {   // IA uniquement pour pré-placer les points d'angle ; ils restent déplaçables
        val b = bmp ?: return
        scope.launch {
            val lm = withContext(Dispatchers.Default) { pose.detect(b) }
            if (lm != null) pts = listOf(lm.shoulder, lm.hip, lm.knee, lm.ankle).map { Offset(it.x.toFloat(), it.y.toFloat()) }
            else info = "Posture non détectée sur cette image : placez les points à la main."
        }
    }

    val frames = src?.frameCount ?: 0
    val tCur = slots[target].first; val kCur = slots[target].second
    val complete = slots.all { (t, k) -> getMark(t, k) != null }
    val portraitVideo = bmp?.let { it.height > it.width } == true

    @Composable fun MarkButton() {
        val existing = getMark(tCur, kCur)
        Button(onClick = { markHere() }, enabled = src != null && measuring == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp), shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White, disabledContainerColor = Surface2, disabledContentColor = TextMid)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ESSAI ${tCur + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Marquer ${markNames[kCur]}", fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (existing != null) Text("actuel : image $existing — rappuyez pour corriger", fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
    @Composable fun MarkArea() {
        if (complete && !editing && measuring == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                relevant.forEach { t ->
                    Button(onClick = { editing = true; target = slots.indexOf(t to 1); getMark(t, 1)?.let { frame = it } },
                        modifier = Modifier.weight(1f).heightIn(min = 68.dp), shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MODIFIER", fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("Essai ${t + 1}", fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MarkButton() }
            if (editing) BtnOutline("Fini", { editing = false }, Modifier.heightIn(min = 68.dp))
        }
    }
    @Composable fun Scrubber() {
        Slider(frame.toFloat(), { frame = it.toInt() }, valueRange = 0f..max(1, frames - 1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = RedBright, activeTrackColor = Red, inactiveTrackColor = Outline))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("−10" to -10, "−1" to -1, "+1" to 1, "+10" to 10).forEach { (l, d) ->
                BtnOutline(l, { frame = (frame + d).coerceIn(0, max(0, frames - 1)) }, Modifier.weight(1f))
            }
        }
        Text("Image $frame / $frames · t = ${fmt(if (fps > 0) frame / fps else 0.0, 3)} s · ${fmt(fps, 1)} ips", color = TextMid, fontSize = 12.sp)
    }
    @Composable fun MeasureControls() {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ptNames.forEachIndexed { i, n -> FilterChip(activePt == i, { activePt = i }, { Text((if (pts[i] != null) "✓ " else "") + n, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi)) }
        }
        Text("Point actif : ${ptNames[activePt]} — touchez l'image pour le placer, ou glissez un point pour le déplacer", color = TextMid, fontSize = 12.sp)
        Text("Genou ${kneeNow?.let { fmt(it, 0) + "°" } ?: "—"}   Hanche ${hipNow?.let { fmt(it, 0) + "°" } ?: "—"}", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BtnOutline("Pré-placer par IA", { detectAI() }, icon = Icons.Filled.AutoAwesome)
            BtnOutline("Effacer", { pts = listOf(null, null, null, null); activePt = 0 })
            BtnOutline("Annuler", { measuring = null })
            BtnPrimary("Valider", { validateAngles() }, enabled = kneeNow != null || hipNow != null)
        }
    }

    PageV(vm, title, onMenu, actions = {
        IconButton({ helpOpen = true }) {
            Box(Modifier.size(26.dp).border(1.5.dp, TextHi, CircleShape), contentAlignment = Alignment.Center) {
                Text("?", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }, header = {
        MarkArea()
        FrameView(bmp, zs, pts, activePt, measuring != null, false, Modifier.fillMaxWidth().height(if (portraitVideo) 340.dp else 230.dp), { fullscreen = true },
            { activePt = it }, { i, o -> movePoint(i, o) }) { placePoint(it) }
        Scrubber()
    }) {
        TextButton({ fpsText = fmt(fps, 1); fpsDialog = true }) { Text("Modifier la cadence (ips)", color = RedBright) }
        if (info.isNotEmpty()) Body(info, Warn, 13)
        if (measuring == null) {
            for (t in relevant) Card2 {
                H("Essai ${t + 1}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in 0..2) {
                        val m = getMark(t, k)
                        FilterChip(target == slots.indexOf(t to k), { target = slots.indexOf(t to k); m?.let { frame = it } }, { Text("${markNames[k]}${m?.let { " · $it" } ?: ""}", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Red, selectedLabelColor = Color.White, labelColor = TextHi))
                    }
                }
                val tA = trials[t].tA(fps)
                if (tA != null) {
                    val h = Mech.heightFromFlight(tA)
                    Body("Temps de vol ${fmt(tA, 3)} s → hauteur ${fmt(h * 100, 1)} cm (±${fmt(2.0 / (fps * tA) * 100, 1)} % pour ±1 image)", TextHi)
                } else Body("Marquer décollage puis réception.", Warn, 13)
                Body("Angles au départ : genou ${trials[t].kneeDeg?.let { fmt(it, 0) + "°" } ?: "—"} · hanche ${trials[t].hipDeg?.let { fmt(it, 0) + "°" } ?: "—"}", size = 13)
                BtnOutline("Mesurer les angles (genou/hanche)", {
                    if (trials[t].startFrame == null) setMark(t, 0, frame)
                    measuring = t; pts = listOf(null, null, null, null); activePt = 0
                    trials[t].startFrame?.let { frame = it }
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
            }
        } else {
            val t = measuring!!
            Card2 {
                H("Angles — essai ${t + 1} (image de départ ${trials[t].startFrame ?: frame})")
                Body("Placez l'image sur la position de départ (genoux ≈ 90°), puis choisissez le point à placer avec les boutons ci-dessous. Voir « ? » pour les repères anatomiques.", size = 13)
                MeasureControls()
            }
        }
        BtnPrimary("Terminer l'analyse de cette vidéo", { vm.pop() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Check)
        Spacer(Modifier.height(24.dp))
    }
    if (helpOpen) AlertDialog(onDismissRequest = { helpOpen = false }, containerColor = Surface2,
        confirmButton = { TextButton({ helpOpen = false }) { Text("Fermer", color = RedBright) } },
        title = { Text("Aide", color = TextHi) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Marqueurs", color = RedBright, fontWeight = FontWeight.Bold)
            Body("Le gros bouton rouge marque l'image affichée. Départ = position basse avant la poussée ; Décollage = dernière image avec contact des pieds ; Réception = première image avec contact. Quand tous les marqueurs sont posés, il devient « Modifier » : choisissez l'essai 1 ou 2, ajustez l'image (−1/+1) puis marquez à nouveau. Vous pouvez aussi toucher un marqueur dans la liste pour y revenir.", size = 13)
            Text("Image", color = RedBright, fontWeight = FontWeight.Bold)
            Body("Zoom : pincez à 2 doigts ou boutons − / +. Déplacez l'image avec un doigt. Plein écran en haut à droite. ☀+ / ☀− éclaircit l'affichage (utile pour les ralentis sombres ; sans effet sur la mesure).", size = 13)
            Text("Angles", color = RedBright, fontWeight = FontWeight.Bold)
            Body("Choisissez le point actif, puis touchez l'image ; glissez un point pour le corriger. Épaule = acromion ; hanche = grand trochanter (le centre réel de l'articulation, la tête fémorale, n'est pas visible de l'extérieur ; repère de Samozino 2008) ; genou = épicondyle latéral ; cheville = malléole latérale.", size = 13)
            RefTag(Refs.S08); RefTag(Refs.MYJ)
        } })
    if (fullscreen) Dialog(onDismissRequest = { fullscreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            FrameView(bmp, zs, pts, activePt, measuring != null, true, Modifier.fillMaxSize(), { fullscreen = false }, { activePt = it }, { i, o -> movePoint(i, o) }) { placePoint(it) }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.82f).background(Color(0xB30A0A0C)).padding(8.dp)) { MarkArea() }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC0A0A0C)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (measuring != null) MeasureControls()
                Scrubber()
            }
        }
    }
    if (fpsDialog) AlertDialog(onDismissRequest = { fpsDialog = false }, containerColor = Surface2,
        title = { Text("Cadence réelle de capture", color = TextHi) },
        text = { Column { Body("À corriger si la vidéo importée est un ralenti dont la cadence détectée est fausse (ex. 240 ips enregistrés, lus à 30 ips).", size = 13); NumField("Images par seconde", fpsText, { fpsText = it }) } },
        confirmButton = { TextButton({ parseD(fpsText)?.takeIf { it in 10.0..1000.0 }?.let { fps = it }; fpsDialog = false }) { Text("Valider", color = RedBright) } },
        dismissButton = { TextButton({ fpsDialog = false }) { Text("Annuler", color = TextHi) } })
}
