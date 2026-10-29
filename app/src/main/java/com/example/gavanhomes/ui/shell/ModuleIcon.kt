package com.example.gavanhomes.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The line icons of the modules, drawn (24 × 24 grid, round caps) so they look the same on every phone:
 * dashboard = home · projects = building · hr = two people · users = a person · professionals = briefcase ·
 * hod = rising chart · audit = document · account = person in a circle · more = four squares.
 */
@Composable
fun ModuleIcon(key: String, color: Color, size: Dp = 22.dp, bold: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        val stroke = Stroke(width = (if (bold) 2.2f else 1.8f) * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun path(build: Path.() -> Unit) = Path().apply(build)
        fun Path.m(x: Float, y: Float) = moveTo(x * k, y * k)
        fun Path.l(x: Float, y: Float) = lineTo(x * k, y * k)
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * k, y1 * k, x2 * k, y2 * k, x * k, y * k)
        fun DrawScope.line(p: Path) = drawPath(p, color, style = stroke)
        fun DrawScope.circle(cx: Float, cy: Float, r: Float) = drawCircle(color, r * k, p(cx, cy), style = stroke)
        fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(color, p(x, y), Size(w * k, h * k), CornerRadius(r * k), style = stroke)

        when (key) {
            "dashboard" -> line(path { m(3f, 11f); l(12f, 3.5f); l(21f, 11f); m(5.5f, 9.5f); l(5.5f, 20.5f); l(10f, 20.5f); l(10f, 14.5f); l(14f, 14.5f); l(14f, 20.5f); l(18.5f, 20.5f); l(18.5f, 9.5f) })
            "projects" -> {
                box(5f, 3f, 14f, 18f, 2f)
                line(path { m(9f, 8f); l(10f, 8f); m(14f, 8f); l(15f, 8f); m(9f, 12f); l(10f, 12f); m(14f, 12f); l(15f, 12f); m(10f, 21f); l(10f, 17f); l(14f, 17f); l(14f, 21f) })
            }
            "hr" -> {
                circle(9f, 8f, 3f); circle(17f, 9f, 2.4f)
                line(path { m(3f, 20f); c(3f, 16.5f, 5.8f, 14f, 9f, 14f); c(12.2f, 14f, 15f, 16.5f, 15f, 20f) })
                line(path { m(16.2f, 14.2f); c(19f, 14f, 21f, 16f, 21f, 19.5f) })
            }
            "users" -> {
                circle(12f, 8f, 4f)
                line(path { m(4f, 21f); c(4f, 16.5f, 7.6f, 14f, 12f, 14f); c(16.4f, 14f, 20f, 16.5f, 20f, 21f) })
            }
            "professionals" -> {
                box(3f, 7f, 18f, 13f, 2.5f)
                line(path { m(9f, 7f); l(9f, 5f); l(15f, 5f); l(15f, 7f); m(3f, 13f); l(21f, 13f); m(11f, 13f); l(13f, 13f) })
            }
            "hod" -> line(path { m(3f, 17f); l(9f, 11f); l(13f, 15f); l(21f, 7f); m(15f, 7f); l(21f, 7f); l(21f, 13f) })
            "audit" -> line(path { m(6f, 3f); l(14f, 3f); l(19f, 8f); l(19f, 21f); l(6f, 21f); close(); m(14f, 3f); l(14f, 8f); l(19f, 8f); m(9f, 13f); l(16f, 13f); m(9f, 17f); l(16f, 17f) })
            "account" -> {
                circle(12f, 12f, 9f); circle(12f, 10f, 3f)
                line(path { m(6.5f, 18.5f); c(8f, 15.8f, 16f, 15.8f, 17.5f, 18.5f) })
            }
            "logout" -> {
                // the power symbol: an open ring with a stem through the gap
                drawArc(color, -60f, 300f, false, p(3.5f, 4.5f), Size(17f * k, 17f * k), style = stroke)
                line(path { m(12f, 2.5f); l(12f, 11f) })
            }
            "more" -> { box(4f, 4f, 6f, 6f, 1.8f); box(14f, 4f, 6f, 6f, 1.8f); box(4f, 14f, 6f, 6f, 1.8f); box(14f, 14f, 6f, 6f, 1.8f) }
            else -> circle(12f, 12f, 8f)
        }
    }
}
