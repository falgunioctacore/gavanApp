package com.example.gavanhomes.ui.users

/** "hr" → "Hr", "superAdmin" → "Super Admin", "view-users" → "View Users". */
internal fun String.headline(): String =
    replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

internal val WeekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Roles that already have every permission (the website hides the permission picker for them). */
internal val FullAccessRoles = setOf("admin", "superAdmin")

private val Verbs = setOf("view", "create", "edit", "delete", "approve", "manage", "export", "import", "assign", "update", "download", "upload", "print", "reject", "send")

/** One group of permissions: the thing they apply to, and each action with the full permission name. */
internal class PermGroup(val name: String, val actions: List<Pair<String, String>>)

/** "view users" + "edit users" → group "users" with actions view / edit; a name without a leading verb is its own group ("access"). */
internal fun permGroups(names: Collection<String>): List<PermGroup> =
    names.map { n ->
        val w = n.trim().split(' ', limit = 2)
        if (w.size == 2 && w[0].lowercase() in Verbs) Triple(w[1], w[0], n) else Triple(n, "access", n)
    }.groupBy { it.first }.toSortedMap().map { (g, rows) -> PermGroup(g, rows.map { it.second to it.third }) }
