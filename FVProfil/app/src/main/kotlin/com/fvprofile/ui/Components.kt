@file:OptIn(ExperimentalMaterial3Api::class)

package com.fvprofile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.text.style.TextAlign
import com.fvprofile.core.Session

fun fmt(d: Double?, n: Int = 2): String = if (d == null || d.isNaN()) "—" else String.format(Locale.FRANCE, "%.${n}f", d)
private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
fun fmtDT(ms: Long): String = dtFmt.format(Date(ms))
fun sessionTitle(s: Session): String = s.name.ifBlank { "Test du " + fmtDT(s.date) }
fun parseD(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

data class Ref(val short: String, val full: String, val note: String)
object Refs {
    val S08 = Ref("Samozino 2008", "Samozino P, Morin JB, Hintzy F, Belli A. A simple method for measuring force, velocity and power output during squat jump. J Biomech. 2008;41:2940–2945.",
        "Calcul de la force, vitesse et puissance moyennes de poussée à partir de la masse, de la hauteur de saut (temps de vol) et de la distance de poussée. Erreur < 3 % vs plateforme de force.")
    val S12 = Ref("Samozino 2012", "Samozino P, Rejc E, Di Prampero PE, Belli A, Morin JB. Optimal force–velocity profile in ballistic movements—Altius: citius or fortius? Med Sci Sports Exerc. 2012;44(2):313–322.",
        "Existence d'un profil F-v optimal (Sfv_opt) pour une Pmax et une distance de poussée données ; un profil défavorable peut coûter jusqu'à 30 % de hauteur de saut à Pmax égale.")
    val M16 = Ref("Morin & Samozino 2016", "Morin JB, Samozino P. Interpreting power-force-velocity profiles for individualized and specific training. Int J Sports Physiol Perform. 2016;11:267–272.",
        "Définitions des variables (F0, V0, Pmax, Sfv, FVimb), protocole de charges (≥ 5 charges de 0 kg jusqu'à ≈ 10 cm de saut) et interprétation individualisée.")
    val J17 = Ref("Jiménez-Reyes 2017", "Jiménez-Reyes P, Samozino P, Brughelli M, Morin JB. Effectiveness of an individualized training based on force-velocity profiling during jumping. Front Physiol. 2017;7:677.",
        "Étude contrôlée (n = 84, 9 semaines) : un entraînement orienté par le déséquilibre F-v améliore le saut chez tous les sujets optimisés, contre 10/18 pour un programme identique pour tous.")
    val MYJ = Ref("Balsalobre-Fernández 2015 ; Stanton 2015", "Cités dans Morin & Samozino 2016 : Balsalobre-Fernández C, Glaister M, Lockey RA. J Sports Sci 2015;33:1574–1579 ; Stanton R, Kean CO, Scanlan AT. Br J Sports Med 2015 (app My Jump).",
        "Mesure de la hauteur de saut par vidéo smartphone image par image (principe repris ici).")
    val all = listOf(S08, S12, M16, J17, MYJ)
}

@Composable
fun RefTag(ref: Ref, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Text("ⓘ ${ref.short}", fontSize = 11.sp, color = TextMid.copy(alpha = 0.85f), modifier = modifier.clickable { open = true })
    if (open) AlertDialog(
        onDismissRequest = { open = false }, containerColor = Surface2,
        confirmButton = { TextButton(onClick = { open = false }) { Text("Fermer", color = RedBright) } },
        title = { Text(ref.short, color = TextHi) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ref.full, color = TextHi, fontSize = 13.sp); Text(ref.note, color = TextMid, fontSize = 13.sp) } }
    )
}

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable fun H(text: String) = Text(text, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
@Composable fun Body(text: String, color: Color = TextMid, size: Int = 14) = Text(text, color = color, fontSize = size.sp, lineHeight = (size + 5).sp)

@Composable
fun Stat(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, accent: Color = TextHi) {
    Column(modifier.background2().padding(10.dp)) {
        Text(label, color = TextMid, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) Text(" $unit", color = TextMid, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
private fun Modifier.background2() = this.then(Modifier.background(Surface2, RoundedCornerShape(10.dp)))

@Composable
fun Expand(title: String, initial: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(initial) }
    Card2 {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = RedBright)
        }
        if (open) content()
    }
}

@Composable
fun BtnPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White, disabledContainerColor = Surface2, disabledContentColor = TextMid)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, textAlign = TextAlign.Center, maxLines = 4, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
fun BtnOutline(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), border = BorderStroke(1.dp, if (enabled) RedBright else Outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi, disabledContentColor = TextMid)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, textAlign = TextAlign.Center, maxLines = 4, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, decimal: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        suffix = if (suffix != null) { { Text(suffix, color = TextMid) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        colors = fieldColors())
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi, unfocusedTextColor = TextHi, focusedBorderColor = RedBright, unfocusedBorderColor = Outline,
    focusedLabelColor = RedBright, unfocusedLabelColor = TextMid, cursorColor = RedBright)

@Composable
fun Page(title: String, stackSize: Int, onBack: () -> Unit, onMenu: () -> Unit,
         actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {},
         header: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = Bg,
        topBar = {
            TopAppBar(title = { Text(title, color = TextHi, maxLines = 1) },
                navigationIcon = {
                    if (stackSize > 1) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    else IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu") }
                }, actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1, titleContentColor = TextHi,
                    navigationIconContentColor = TextHi, actionIconContentColor = TextHi))
        }, floatingActionButton = fab) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (header != null) Column(Modifier.fillMaxWidth().background(Bg).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), content = header)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Supprimer", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface2,
        title = { Text(title, color = TextHi) }, text = { Text(text, color = TextMid) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = RedBright) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = TextHi) } })
}
