package com.example.gavanhomes.ui.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.ui.common.Branding
import com.example.gavanhomes.ui.common.KitCard
import com.example.gavanhomes.ui.common.KitInk
import com.example.gavanhomes.ui.common.KitMuted
import com.example.gavanhomes.ui.common.KitSoft
import com.example.gavanhomes.ui.common.PersonAvatar
import com.example.gavanhomes.ui.common.SearchBox

/**
 * Pick one person — the same look as the task page's assignee picker: a search box, then everyone with their avatar (profile picture, or
 * initials), the current one ticked. [Choice.value] is the user's id. [allowNone] adds a "nobody" row at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonChoiceSheet(title: String, choices: List<Choice>, current: String?, b: Branding, allowNone: Boolean = false, noneLabel: String = "Nobody", onClose: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = KitCard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KitInk)
            SearchBox(query, { query = it }, "Search by name")
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowNone && query.isBlank()) {
                    val on = current.isNullOrBlank()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else KitSoft).clickable { onPick("") }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(KitMuted.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text("—", color = KitMuted, fontWeight = FontWeight.Bold) }
                        Text(noneLabel, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                    }
                }
                val shown = choices.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                if (shown.isEmpty()) Text("Nobody matches.", color = KitMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                shown.forEach { c ->
                    val on = current == c.value
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) b.base.copy(alpha = .14f) else KitSoft).clickable { onPick(c.value) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(c.value.toIntOrNull() ?: 0, c.label, 34.dp)
                        Text(c.label, color = KitInk, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
