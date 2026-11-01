package com.aicanvas.app.phone

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** A quick "is the phone connected to anything?" check, so we can say so instead of waiting for a timeout. */
object Network {
    const val OFFLINE_MESSAGE = "You're offline. Connect to Wi-Fi or mobile data and try again — your message is still here."

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
