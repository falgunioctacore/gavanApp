package com.example.gavanhomes.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.User

/**
 * One entry per website module (the same list as the web menu). Modules with [native] = true have a screen in
 * this app (ui/<module>/); the rest open the website page in the in-app browser.
 */
class Module(val key: String, val label: String, val glyph: String, val path: String, val native: Boolean)

/** The modules this user can see, in the website's menu order. */
fun modulesFor(user: User): List<Module> {
    val admin = user.roles.any { it.equals("superAdmin", true) || it.equals("admin", true) }
    val superAdmin = user.roles.any { it.equals("superAdmin", true) }
    return buildList {
        add(Module("dashboard", "Dashboards", "⌂", "/admin/dashboard", native = true))
        if (user.can("view projects")) add(Module("projects", "Projects", "▦", "/admin/projects", native = true))
        if (superAdmin || user.can("view users")) add(Module("users", "Users", "◎", "/admin/roles", native = true))
        if (superAdmin) add(Module("professionals", "Professionals", "✦", "/admin/professionals/dashboard", native = true))
        if (admin || user.can("view hr")) add(Module("hr", "HR & Admin", "✚", "/admin/hr", native = true))
        if (admin || user.can("view hod dashboard")) add(Module("hod", "HOD Sales", "↗", "/admin/hod", native = false))
        if (superAdmin) add(Module("audit", "Audit Logs", "◈", "/admin/audit-logs", native = false))
        add(Module("account", "Account", "◉", "/admin/pages/account-settings-account", native = true))
    }
}

/** Floating pill of modules, like the website's bottom bar: the active one shows its label on a dark pill. */
@Composable
fun ModuleBar(modules: List<Module>, activeKey: String, onSelect: (Module) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(Color.White)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        modules.forEach { m ->
            val on = m.key == activeKey
            Row(
                Modifier.clip(RoundedCornerShape(24.dp)).background(if (on) Color(0xFF0F172A) else Color.Transparent)
                    .clickable { onSelect(m) }.padding(horizontal = if (on) 16.dp else 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.glyph, fontSize = 20.sp, color = if (on) Color.White else Color(0xFF8A8F9C))
                if (on) Text(m.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
