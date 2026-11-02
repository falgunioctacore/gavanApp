package com.example.gavanhomes.ui.discussion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.gavanhomes.MainActivity
import com.example.gavanhomes.data.ApiClient
import com.example.gavanhomes.data.SessionStore
import com.example.gavanhomes.ui.common.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Chat and update alerts in the notification bar, like WhatsApp:
 *  • every chat with new messages gets one notification (the last few messages, the sender's initial as a picture, project name), with
 *    **Reply** (type in the bar) and **Mark as read**; several chats are bundled under one summary;
 *  • new task / target / lead / bid notifications arrive as updates with **Mark as read**;
 *  • [check] / [checkUpdates] look for them — every few seconds while the app is open (the shell) and about every 15 minutes in the
 *    background ([ChatCheckWorker]); the first look only remembers "now", so the history is never announced.
 * Messages in the chat that is open right now are not announced. Server side: GET /discussion/inbox · /notifications/new · POST …/read.
 */
object ChatAlerts {
    const val PREFS = "chat_alerts"
    const val CHANNEL = "chat_messages"
    const val GROUP = "gavan_chats"
    const val SUMMARY_ID = 41_000
    const val UPDATES = "app_updates"
    const val UPDATES_GROUP = "gavan_updates"
    const val UPDATES_SUMMARY_ID = 41_001
    const val ACTION_REPLY = "com.example.gavanhomes.CHAT_REPLY"
    const val ACTION_READ = "com.example.gavanhomes.CHAT_READ"
    const val ACTION_NOTE_READ = "com.example.gavanhomes.NOTE_READ"
    const val KEY_REPLY = "reply_text"

    private const val BRAND = 0xFF1E5FAE.toInt()

    /** A chat the person asked to open (tapped a notification, or picked one): the shell opens it and clears this. */
    var pending by mutableStateOf<ChatLink?>(null)

    /** Set when an update notification was tapped: the shell opens the notifications screen and clears this. */
    var openNotifications by mutableStateOf(false)

    /** The conversation on screen right now (its messages are not announced). */
    @Volatile var openConv: String? = null

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** An API client that signs with the saved token — usable from the background job and the notification buttons. */
    internal fun apiFor(context: Context): ApiClient {
        val session = SessionStore(context)
        return ApiClient { session.token }
    }

    private fun idFor(conv: String): Int = abs(conv.hashCode() % 900_000) + 1_000

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Chat messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages in project discussions"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    /** A round picture of the sender's initials, coloured from their name — the avatar beside a message. */
    private fun avatar(name: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val palette = intArrayOf(0xFF1E5FAE.toInt(), 0xFF2E7D52.toInt(), 0xFFC47A1A.toInt(), 0xFF6A4FB3.toInt(), 0xFFB93232.toInt(), 0xFF1B8A8F.toInt(), 0xFFB8527A.toInt())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[abs(name.hashCode()) % palette.size] }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, fill)
        val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().toString().uppercase(Locale.ROOT) }.ifBlank { "?" }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        canvas.drawText(initials, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bmp
    }

    private fun action(context: Context, action: String, project: String, conv: String, title: String): Intent =
        Intent(context, ChatActionReceiver::class.java).setAction(action).putExtra("project", project).putExtra("conv", conv).putExtra("title", title)

    // ── chat messages ────────────────────────────────────────────────────────────────────────────

    /** Announces [fresh] messages of one chat: keeps the last few, shows them as one conversation notification, then refreshes the summary. */
    internal fun show(context: Context, conv: String, fresh: List<JSONObject>) {
        if (fresh.isEmpty() || !Downloads.canNotify(context)) return
        channel(context)
        val key = "lines:$conv"
        val lines = runCatching { JSONArray(prefs(context).getString(key, "[]")) }.getOrDefault(JSONArray())
        fresh.forEach { lines.put(it) }
        val kept = JSONArray()
        for (i in max(0, lines.length() - 8) until lines.length()) kept.put(lines.getJSONObject(i))
        prefs(context).edit().putString(key, kept.toString()).apply()

        val last = fresh.last()
        val project = last.optString("project")
        val chat = last.optString("chat")
        val group = last.optBoolean("group")

        val me = Person.Builder().setName("You").setKey("me").build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(group).setConversationTitle(if (group) chat else null)
        var unread = 0
        for (i in 0 until kept.length()) {
            val m = kept.getJSONObject(i)
            if (!m.optBoolean("mine")) unread++
            val sender = Person.Builder().setName(m.optString("sender")).setKey(m.optString("sender_key"))
                .setIcon(IconCompat.createWithBitmap(avatar(m.optString("sender")))).build()
            val at = runCatching { OffsetDateTime.parse(m.optString("at")).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            style.addMessage(m.optString("body"), at, sender)
        }

        val id = idFor(conv)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("chat_project", project).putExtra("chat_conv", conv).putExtra("chat_title", chat),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val replyIntent = PendingIntent.getBroadcast(context, id + 1, action(context, ACTION_REPLY, project, conv, chat), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
        val reply = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyIntent)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(true).build()
        val readIntent = PendingIntent.getBroadcast(context, id + 2, action(context, ACTION_READ, project, conv, chat), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val read = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, "Mark as read", readIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat).setColor(BRAND).setStyle(style)
            .setContentTitle(if (group) chat else last.optString("sender")).setContentText(last.optString("sender") + ": " + last.optString("body"))
            .setSubText(last.optString("project_name").takeIf { it.isNotBlank() })
            .setLargeIcon(avatar(if (group) chat else last.optString("sender")))
            .setNumber(unread).setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open).addAction(reply).addAction(read).setGroup(GROUP).setAutoCancel(true).setOnlyAlertOnce(false).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        summary(context)
    }

    /** One line over several chats: "7 messages from 3 chats" — Android bundles the chat notifications under it. */
    private fun summary(context: Context) {
        val all = prefs(context).all.filterKeys { it.startsWith("lines:") }
        if (all.size < 2) return
        var messages = 0
        all.values.forEach { v -> runCatching { val a = JSONArray(v as String); for (i in 0 until a.length()) if (!a.getJSONObject(i).optBoolean("mine")) messages++ } }
        val text = "$messages messages from ${all.size} chats"
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_chat).setColor(BRAND)
            .setContentTitle("Gavan Homes").setContentText(text).setStyle(NotificationCompat.InboxStyle().setSummaryText(text))
            .setGroup(GROUP).setGroupSummary(true).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, n) }
    }

    /** Looks for chat messages newer than the last one seen and announces them (not those of the chat that is open, nor your own). */
    suspend fun check(context: Context, api: ApiClient) {
        val app = context.applicationContext
        val after = prefs(app).getInt("after", 0)
        val r = withContext(Dispatchers.IO) { api.get("/discussion/inbox?after_id=$after") }
        val latest = r.optInt("latest_id")
        val list = r.optJSONArray("messages") ?: JSONArray()
        if (after <= 0) { prefs(app).edit().putInt("after", latest).apply(); return }      // the first look: start from now
        val byChat = LinkedHashMap<String, MutableList<JSONObject>>()
        var top = after
        for (i in 0 until list.length()) {
            val m = list.getJSONObject(i)
            top = max(top, m.optInt("id"))
            val conv = m.optString("conv")
            if (m.optBoolean("mine") || conv == openConv) continue
            byChat.getOrPut(conv) { ArrayList() }.add(m)
        }
        prefs(app).edit().putInt("after", max(latest, top)).apply()
        for ((conv, msgs) in byChat) show(app, conv, msgs)
    }

    // ── updates: tasks, targets, leads, bids ─────────────────────────────────────────────────────

    /** Looks for new task / target / lead / bid notifications and announces each (several are bundled under a "N new notifications" summary). */
    suspend fun checkUpdates(context: Context, api: ApiClient) {
        val app = context.applicationContext
        val after = prefs(app).getString("notes_after", "") ?: ""
        val r = withContext(Dispatchers.IO) { api.get("/notifications/new" + if (after.isBlank()) "" else "?after=" + Uri.encode(after)) }
        prefs(app).edit().putString("notes_after", r.optString("cursor")).apply()
        val arr = r.optJSONArray("notifications") ?: return
        if (arr.length() == 0 || !Downloads.canNotify(app)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(UPDATES) == null) {
                nm.createNotificationChannel(NotificationChannel(UPDATES, "Tasks, targets & updates", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Task, target, lead and bid notifications" })
            }
        }
        val open = PendingIntent.getActivity(
            app, UPDATES_SUMMARY_ID, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_notifications", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nm = NotificationManagerCompat.from(app)
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (items.size > 1) {
            val inbox = NotificationCompat.InboxStyle().setSummaryText("${items.size} new")
            items.take(6).forEach { inbox.addLine(it.optString("title")) }
            val summary = NotificationCompat.Builder(app, UPDATES).setSmallIcon(android.R.drawable.stat_notify_more).setColor(BRAND)
                .setContentTitle("${items.size} new notifications").setContentText(items.first().optString("title")).setStyle(inbox)
                .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup(UPDATES_GROUP).setGroupSummary(true).setNumber(items.size).build()
            runCatching { nm.notify(UPDATES_SUMMARY_ID, summary) }
        }
        items.forEach { it ->
            val id = abs(it.optString("id").hashCode() % 900_000) + 2_000
            val note = PendingIntent.getBroadcast(
                app, id, Intent(app, ChatActionReceiver::class.java).setAction(ACTION_NOTE_READ).putExtra("note", it.optString("id")).putExtra("nid", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val message = it.optString("message").ifBlank { it.optString("title") }
            val whenMs = runCatching { OffsetDateTime.parse(it.optString("created_at")).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            val n = NotificationCompat.Builder(app, UPDATES).setSmallIcon(if (it.optString("kind") == "task" || it.optString("kind") == "target") android.R.drawable.ic_menu_agenda else android.R.drawable.stat_notify_more)
                .setColor(BRAND).setContentTitle(it.optString("title")).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSubText(it.optString("site").takeIf { s -> s.isNotBlank() && s != "null" }).setWhen(whenMs).setShowWhen(true)
                .setContentIntent(open).addAction(0, "Mark as read", note).setGroup(UPDATES_GROUP).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setDefaults(NotificationCompat.DEFAULT_ALL).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
            runCatching { nm.notify(id, n) }
        }
    }

    // ── housekeeping ─────────────────────────────────────────────────────────────────────────────

    /** The chat was read (opened, replied to, or marked as read): forget its lines and take its notification down. */
    fun clear(context: Context, conv: String) {
        val app = context.applicationContext
        prefs(app).edit().remove("lines:$conv").apply()
        val nm = NotificationManagerCompat.from(app)
        nm.cancel(idFor(conv))
        if (prefs(app).all.keys.none { it.startsWith("lines:") }) nm.cancel(SUMMARY_ID)
    }

    /** You answered from the notification: the chat counts as read. */
    internal fun replied(context: Context, conv: String) = clear(context, conv)

    /** Signing out: take every chat notification down, forget what was seen, and stop the background job. */
    fun reset(context: Context) {
        val app = context.applicationContext
        val nm = NotificationManagerCompat.from(app)
        prefs(app).all.keys.filter { it.startsWith("lines:") }.forEach { nm.cancel(idFor(it.removePrefix("lines:"))) }
        nm.cancel(SUMMARY_ID); nm.cancel(UPDATES_SUMMARY_ID)
        prefs(app).edit().clear().apply()
        WorkManager.getInstance(app).cancelUniqueWork(ChatCheckWorker.NAME)
    }

    /** Starts the background job (about every 15 minutes, only with a connection); asking again keeps the one already scheduled. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ChatCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(ChatCheckWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
