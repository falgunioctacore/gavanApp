package com.example.gavanhomes.ui.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where an exported file goes: the phone's **Downloads** folder (Downloads/GavanHomes), so it shows up in the Files / Downloads app —
 * plus a notification ("Export ready") that opens it when tapped, like any other download.
 */
object Downloads {
    private const val CHANNEL = "downloads"

    /** Saves [bytes] as [name] in Downloads and returns a link to it that other apps can read. */
    fun save(context: Context, name: String, mime: String, bytes: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GavanHomes")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the file in Downloads")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri
        }
        // older phones: the app's own Downloads folder (no storage permission needed); the notification still opens it
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GavanHomes").apply { mkdirs() }
        val file = File(dir, name).also { it.writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** True when the app may post notifications (always before Android 13). */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Export ready — <file>": tap to open it, or Share. Quietly does nothing when notifications are switched off. */
    fun notifyReady(context: Context, title: String, name: String, mime: String, uri: Uri) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Files you export from the app" })
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // no app for this kind of file? the Downloads list still opens
        val open = if (view.resolveActivity(context.packageManager) != null) view else Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val share = Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(name).setSubText("Downloads/GavanHomes")
            .setContentIntent(PendingIntent.getActivity(context, id, open, flags))
            .addAction(0, "Share", PendingIntent.getActivity(context, id + 1, share, flags))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        manager.notify(id, notification)
    }
}
