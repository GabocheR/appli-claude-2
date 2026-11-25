package com.fvprofile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.*

data class Series(
    val pts: List<Pair<Double, Double>>, val color: Color, val label: String = "",
    val dots: Boolean = true, val line: Boolean = false, val dashed: Boolean = false, val hollow: Boolean = false
)

private fun niceStep(range: Double): Double {
    val raw = range / 4.0
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
}

@Composable
fun XYChart(series: List<Series>, xTitle: String, yTitle: String, modifier: Modifier = Modifier,
            xMinZero: Boolean = false, yMinZero: Boolean = false, xTicks: List<Pair<Double, String>>? = null) {
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(color = TextMid, fontSize = 10.sp)
    val all = series.flatMap { it.pts }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(250.dp).background(Surface2, RoundedCornerShape(10.dp))) {
            if (all.isEmpty()) return@Canvas
            var x0 = all.minOf { it.first }; var x1 = all.maxOf { it.first }
            var y0 = all.minOf { it.second }; var y1 = all.maxOf { it.second }
            if (xMinZero) x0 = min(0.0, x0); if (yMinZero) y0 = min(0.0, y0)
            if (x1 - x0 < 1e-9) { x0 -= 1; x1 += 1 }
            if (y1 - y0 < 1e-9) { y0 -= 1; y1 += 1 }
            val px = (x1 - x0) * 0.06; val py = (y1 - y0) * 0.10
            if (!(xMinZero)) { x0 -= px }; x1 += px
            if (!(yMinZero)) { y0 -= py }; y1 += py
            val l = 46.dp.toPx(); val r = 10.dp.toPx(); val t = 10.dp.toPx(); val b = 34.dp.toPx()
            val w = size.width - l - r; val h = size.height - t - b
            fun X(v: Double) = (l + (v - x0) / (x1 - x0) * w).toFloat()
            fun Y(v: Double) = (t + h - (v - y0) / (y1 - y0) * h).toFloat()
            // grille + graduations
            val ys = niceStep(y1 - y0); var gy = ceil(y0 / ys) * ys
            while (gy <= y1) {
                drawLine(Outline, Offset(l, Y(gy)), Offset(l + w, Y(gy)), 1f)
                val tl = tm.measure(fmt(gy, if (ys < 1) 1 else 0), axisStyle)
                drawText(tl, topLeft = Offset(l - tl.size.width - 4.dp.toPx(), Y(gy) - tl.size.height / 2f)); gy += ys
            }
            val ticks = xTicks ?: run { val xs = niceStep(x1 - x0); val list = ArrayList<Pair<Double, String>>(); var gx = ceil(x0 / xs) * xs
                while (gx <= x1) { list.add(gx to fmt(gx, if (xs < 1) 1 else 0)); gx += xs }; list }
            for ((v, s) in ticks) {
                drawLine(Outline, Offset(X(v), t), Offset(X(v), t + h), 1f)
                val tl = tm.measure(s, axisStyle)
                drawText(tl, topLeft = Offset(X(v) - tl.size.width / 2f, t + h + 3.dp.toPx()))
            }
            drawLine(TextMid, Offset(l, t + h), Offset(l + w, t + h), 2f); drawLine(TextMid, Offset(l, t), Offset(l, t + h), 2f)
            val xt = tm.measure(xTitle, axisStyle); drawText(xt, topLeft = Offset(l + w / 2 - xt.size.width / 2f, size.height - xt.size.height - 1.dp.toPx()))
            val yt = tm.measure(yTitle, axisStyle); drawText(yt, topLeft = Offset(2.dp.toPx(), 0f))
            for (s in series) {
                if (s.line && s.pts.size > 1) {
                    val eff = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
                    for (i in 0 until s.pts.size - 1)
                        drawLine(s.color, Offset(X(s.pts[i].first), Y(s.pts[i].second)), Offset(X(s.pts[i + 1].first), Y(s.pts[i + 1].second)), 4f, pathEffect = eff)
                }
                if (s.dots) for (p in s.pts) {
                    if (s.hollow) drawCircle(s.color, 6.dp.toPx(), Offset(X(p.first), Y(p.second)), style = Stroke(3f))
                    else drawCircle(s.color, 6.dp.toPx(), Offset(X(p.first), Y(p.second)))
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            for (s in series) if (s.label.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(s.color, RoundedCornerShape(2.dp))); Spacer(Modifier.width(4.dp))
                Text(s.label, color = TextMid, fontSize = 11.sp)
            }
        }
    }
}
