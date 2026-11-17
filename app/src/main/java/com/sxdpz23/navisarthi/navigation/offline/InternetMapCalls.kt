package com.sxdpz23.navisarthi.navigation.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** Select internet per request; never route remote tiles through the console's local Wi-Fi. */
internal class InternetMapCalls(context: Context, private val base: OkHttpClient) : Call.Factory {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var selected: Network? = null
    private var client = base

    @Synchronized override fun newCall(request: Request): Call {
        fun usable(network: Network): Boolean = connectivity.getNetworkCapabilities(network)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        val internet = connectivity.activeNetwork?.takeIf(::usable)
            ?: connectivity.allNetworks.firstOrNull(::usable)
        if (selected != internet) {
            selected = internet
            client = if (internet == null) base else base.newBuilder()
                .socketFactory(internet.socketFactory)
                .dns { host -> internet.getAllByName(host).toList() }
                .connectionPool(okhttp3.ConnectionPool())
                .build()
        }
        return client.newCall(request)
    }
}
