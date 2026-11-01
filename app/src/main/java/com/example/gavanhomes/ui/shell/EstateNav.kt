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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gavanhomes.data.User
import com.example.gavanhomes.ui.common.Branding

private val Ink: Color get() = com.example.gavanhomes.ui.common.KitInk
private val Muted: Color get() = com.example.gavanhomes.ui.common.KitMuted
private val Page: Color get() = com.example.gavanhomes.ui.common.KitPage

/** The app's five places, in a property business's terms. */
internal class Place(val key: String, val label: String, val icon: String)

internal val TeamKeys = listOf("users", "hr", "professionals")
internal val MeKeys = listOf("account", "audit", "appSettings", "me")

/** The places this person has something in: Home · Sales · Projects · Team · Me. */
internal fun placesFor(modules: List<Module>): List<Place> {
    val keys = modules.map { it.key }.toSet()
    return listOfNotNull(
        Place("home", "Home", "dashboard"),
        Place("sales", "Sales", "hod").takeIf { "hod" in keys },
        Place("projects", "Projects", "projects").takeIf { "projects" in keys },
        Place("team", "Team", "users").takeIf { TeamKeys.any { it in keys } },
        Place("me", "Me", "account"),
    )
}

/** Which place a screen belongs to, so its tab lights up. */
internal fun placeOf(base: String): String = when (base) {
    "dashboard" -> "home"
    "hod" -> "sales"
    "projects" -> "projects"
    in TeamKeys, "team" -> "team"
    else -> "me"
}

/**
 * The bottom bar: a floating white bar with rounded ends; Projects — the heart of a property business — sits raised in the middle
 * as a round brand-coloured button. The open place shows its icon in the brand colour with a small dot under its name.
 */
@Composable
internal fun EstateNavBar(places: List<Place>, active: String, b: Branding, onSelect: (String) -> Unit) {
    val quiet = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth().background(Page).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(RoundedCornerShape(32.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            places.forEach { p ->
                val on = p.key == active
                if (p.key == "projects") {
                    // the raised centre button
                    Column(Modifier.weight(1f).clickable(interactionSource = quiet, indication = null) { onSelect(p.key) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.offset(y = (-14).dp).size(56.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(b.base, b.deep), start = Offset.Zero, end = Offset(160f, 160f)))
                                .border(4.dp, com.example.gavanhomes.ui.common.KitCard, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { ModuleIcon(p.icon, Color.White, 24.dp, bold = true) }
                        Text(p.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) b.deep else Muted, modifier = Modifier.offset(y = (-10).dp))
                    }
                } else {
                    Column(
                        Modifier.weight(1f).clickable(interactionSource = quiet, indication = null) { onSelect(p.key) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        ModuleIcon(p.icon, if (on) b.base else Muted, 23.dp, bold = on)
                        Text(p.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Ink else Muted, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        Box(Modifier.padding(top = 3.dp).size(if (on) 5.dp else 0.dp).clip(CircleShape).background(b.base))
                    }
                }
            }
        }
    }
}

/** One tile of a hub: a coloured icon, a name, a line about it, and an arrow. */
internal class HubItem(val key: String, val title: String, val blurb: String, val icon: String, val tint: Color)

/**
 * A hub screen (Team, Me): a brand-coloured header with the place's name and a line about it, an optional card under it, then
 * large tiles — tap one to open it.
 */
@Composable
internal fun HubScreen(b: Branding, title: String, subtitle: String, items: List<HubItem>, header: (@Composable () -> Unit)? = null, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Page).verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
                .background(Brush.linearGradient(listOf(b.deep, b.base), start = Offset.Zero, end = Offset(900f, 1100f))),
        ) {
            Box(Modifier.size(200.dp).offset(x = 240.dp, y = (-50).dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
            Box(Modifier.size(120.dp).offset(x = (-30).dp, y = 90.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))
            Column(Modifier.statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = if (header != null) 64.dp else 30.dp)) {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            }
        }
        header?.let { Box(Modifier.offset(y = (-44).dp).padding(horizontal = 16.dp)) { it() } }
        Column(
            Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp).padding(top = if (header != null) 0.dp else 16.dp, bottom = 24.dp).offset(y = if (header != null) (-30).dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { it -> HubTile(it) { onOpen(it.key) } }
        }
    }
}

@Composable
private fun HubTile(i: HubItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(com.example.gavanhomes.ui.common.KitCard).clickable { onClick() }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(i.tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) { ModuleIcon(i.icon, i.tint, 26.dp, bold = true) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(i.title, color = if (i.key == "logout") i.tint else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(i.blurb, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(Page), contentAlignment = Alignment.Center) { Text("›", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Team: the people side of the business — staff and roles, HR, outside professionals. */
@Composable
internal fun TeamHub(modules: List<Module>, b: Branding, onOpen: (String) -> Unit) {
    val keys = modules.map { it.key }.toSet()
    val items = listOfNotNull(
        HubItem("users", "Users & roles", "Staff logins, roles and who can do what", "users", Color(0xFF2F6FE4)).takeIf { "users" in keys },
        HubItem("hr", "HR & Admin", "Employees, attendance, payroll and leave", "hr", Color(0xFF0E8A50)).takeIf { "hr" in keys },
        HubItem("professionals", "Professionals", "Architects, agents, consultants and their reviews", "professionals", Color(0xFF8B5CF6)).takeIf { "professionals" in keys },
    )
    HubScreen(b, "Team", "The people who build and sell your projects", items, onOpen = onOpen)
}

/** Me: your profile, your notifications, and the admin tools, with Log out. */
@Composable
internal fun MeHub(user: User, modules: List<Module>, b: Branding, superAdmin: Boolean, unread: Int, onOpen: (String) -> Unit) {
    val keys = modules.map { it.key }.toSet()
    val items = listOfNotNull(
        HubItem("account", "Profile & password", "Your details, photo and password", "account", if (com.example.gavanhomes.ui.common.AppTheme.dark) b.base else b.deep),
        HubItem("appearance", "Appearance", when (com.example.gavanhomes.ui.common.AppTheme.mode) { "dark" -> "Dark"; "light" -> "Light"; else -> "Follows the phone" } + " · tap to change", "projects", Color(0xFF6250D6)),
        HubItem("notifications", "Notifications", if (unread > 0) "$unread unread" else "You're all caught up", "dashboard", Color(0xFFC47A1A)),
        HubItem("audit", "Audit logs", "Every change made in the system, and by whom", "audit", Color(0xFF475569)).takeIf { "audit" in keys },
        HubItem("appSettings", "App settings", "Company name, logo and brand colours", "projects", Color(0xFF0E8A7E)).takeIf { superAdmin },
        HubItem("logout", "Log out", "Sign out of this phone", "logout", Color(0xFFD03B3B)),
    )
    HubScreen(b, "Me", "Your account and settings", items, header = {
        Row(
            Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(com.example.gavanhomes.ui.common.KitCard).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(b.base, b.deep))), contentAlignment = Alignment.Center) {
                Text(user.name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1) }.uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(user.name, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.email, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    user.roles.firstOrNull()?.replace(Regex("([a-z])([A-Z])"), "$1 $2")?.replaceFirstChar { it.uppercase() } ?: "Member",
                    color = if (com.example.gavanhomes.ui.common.AppTheme.dark) b.base else b.deep, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(b.base.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }, onOpen = onOpen)
}

/** Light, dark, or follow the phone — the choice applies at once and is kept on this phone. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(b: Branding, onClose: () -> Unit, onPick: (String) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = com.example.gavanhomes.ui.common.KitCard) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Appearance", color = com.example.gavanhomes.ui.common.KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("How the app looks on this phone", color = com.example.gavanhomes.ui.common.KitMuted, fontSize = 13.sp)
            listOf(Triple("light", "☀  Light", "Bright pages, for daytime"), Triple("dark", "☾  Dark", "Dark pages, easy on the eyes at night"), Triple("system", "◐  Follow the phone", "Switches with the phone's own setting")).forEach { (k, l, sub) ->
                val on = com.example.gavanhomes.ui.common.AppTheme.mode == k
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) b.base.copy(alpha = .16f) else com.example.gavanhomes.ui.common.KitSoft)
                        .border(1.5.dp, if (on) b.base else Color.Transparent, RoundedCornerShape(16.dp)).clickable { onPick(k) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l, color = com.example.gavanhomes.ui.common.KitInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(sub, color = com.example.gavanhomes.ui.common.KitMuted, fontSize = 12.sp)
                    }
                    if (on) Text("✓", color = b.base, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }
    }
}
