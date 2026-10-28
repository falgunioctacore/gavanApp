package com.example.gavanhomes.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF8A8F9C)

/** The modules that earn a place on the bar, most important first; whatever else a user can open goes under "More". */
private val BarPriority = listOf("dashboard", "projects", "hr", "users", "professionals", "hod", "audit", "account")
private const val BarSlots = 3

private val MoreModule = Module("more", "More", "⋯", "", native = true)

/**
 * The footer tabs: three key modules (Dashboards, Projects, HR & Admin) and a **More** tab that opens a sheet with the rest. A full-width white bar with softly rounded top
 * corners; the open tab gets a brand-tinted capsule behind its icon, a bold label and a short accent line. When the open module lives
 * under More, the More tab is the one that lights up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleBar(modules: List<Module>, activeKey: String, accent: Color = Color(0xFF3670F7), onSelect: (Module) -> Unit) {
    val ranked = modules.sortedBy { BarPriority.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    val onBar = ranked.take(if (ranked.size > BarSlots + 1) BarSlots else ranked.size)
    val more = ranked.drop(onBar.size)
    val tabs = if (more.isEmpty()) onBar else onBar + MoreModule
    var sheet by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), ambientColor = Color(0x1A0F172A), spotColor = Color(0x1A0F172A))
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Color.White).navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
            tabs.forEach { m ->
                val isMore = m.key == "more"
                val on = if (isMore) more.any { it.key == activeKey } else m.key == activeKey
                Column(
                    Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (isMore) sheet = true else onSelect(m) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(if (on) 26.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(accent))
                    Box(
                        Modifier.padding(top = 7.dp).size(width = 54.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) accent.copy(alpha = .16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(m.glyph, fontSize = if (isMore) 24.sp else 19.sp, color = if (on) accent else Muted) }
                    Text(
                        m.label, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Ink else Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }

    // ── More: everything that isn't on the bar ──
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("More", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
            Text("The rest of your modules", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 14.dp))
            more.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { m ->
                        val on = m.key == activeKey
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) accent.copy(alpha = .12f) else Color(0xFFF7F5F1))
                                .border(1.dp, if (on) accent.copy(alpha = .5f) else Color(0xFFEFECE6), RoundedCornerShape(20.dp))
                                .clickable { sheet = false; onSelect(m) }.padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (on) accent else Color.White), contentAlignment = Alignment.Center) {
                                Text(m.glyph, fontSize = 22.sp, color = if (on) Color.White else accent)
                            }
                            Text(m.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
