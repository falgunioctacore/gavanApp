package com.example.gavanhomes.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the phone is online, kept up to date by the system. [online] drives the "No internet" banner; [tick] goes up each time a connection
 * comes (back), so a screen that failed for lack of internet can load again by itself.
 */
object Net {
    var online by mutableStateOf(true)
    var tick by mutableIntStateOf(0)
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { if (!online) tick++; online = true }
                override fun onLost(network: Network) { online = false }
                override fun onUnavailable() { online = false }
            })
        }
    }
}

/** The request never reached the server (no internet, a dead connection, a timeout): the message says so in plain words. */
class NoConnectionException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

const val OFFLINE_TEXT = "No internet connection. Check your Wi-Fi or mobile data and try again."
