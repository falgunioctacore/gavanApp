package com.example.gavanhomes.ui.discussion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs about every 15 minutes in the background, even when the app is closed: looks for new chat messages and new task / target / lead / bid
 * notifications and announces them in the notification bar. Retries later when there was no connection.
 */
class ChatCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val api = ChatAlerts.apiFor(applicationContext)
        if (SessionToken.of(applicationContext) == null) return Result.success()      // signed out: nothing to look for
        val chats = runCatching { ChatAlerts.check(applicationContext, api) }
        val updates = runCatching { ChatAlerts.checkUpdates(applicationContext, api) }
        return if (chats.isSuccess || updates.isSuccess) Result.success() else Result.retry()
    }

    companion object { const val NAME = "chat_check" }
}

private object SessionToken {
    fun of(context: Context): String? = com.example.gavanhomes.data.SessionStore(context).token
}
