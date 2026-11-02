package com.example.gavanhomes.ui.discussion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The buttons on a notification: **Reply** (type an answer right in the notification bar), **Mark as read** on a chat, and **Mark as read** on
 * an update. They run without opening the app.
 */
@OptIn(DelicateCoroutinesApi::class)
class ChatActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val api = ChatAlerts.apiFor(app)
        when (intent.action) {
            ChatAlerts.ACTION_NOTE_READ -> {
                val note = intent.getStringExtra("note").orEmpty()
                NotificationManagerCompat.from(app).cancel(intent.getIntExtra("nid", 0))
                val done = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    runCatching { api.send("POST", "/notifications/$note/read", JSONObject()) }
                    done.finish()
                }
            }
            else -> {
                val project = intent.getStringExtra("project").orEmpty()
                val conv = intent.getStringExtra("conv").orEmpty()
                val done = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    val base = "/projects/$project/discussion-center/chats/$conv"
                    try {
                        when (intent.action) {
                            ChatAlerts.ACTION_READ -> {
                                runCatching { api.send("POST", "$base/read", JSONObject()) }
                                ChatAlerts.clear(app, conv)
                            }
                            ChatAlerts.ACTION_REPLY -> {
                                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatAlerts.KEY_REPLY)?.toString()?.trim().orEmpty()
                                if (text.isNotEmpty()) {
                                    val ok = runCatching { api.send("POST", "$base/send", JSONObject().put("body", text)) }.isSuccess
                                    if (ok) ChatAlerts.replied(app, conv)
                                    else Handler(Looper.getMainLooper()).post { Toast.makeText(app, "Couldn't send — open the chat to try again", Toast.LENGTH_LONG).show() }
                                }
                            }
                        }
                    } finally { done.finish() }
                }
            }
        }
    }
}
