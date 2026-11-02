package com.scantrack.qr.presentation.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.scantrack.qr.data.parser.WiFiNetworkData
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, lifecycle-aware Wi-Fi connection manager.
 * Prevents stale callbacks from unregistering newer requests, tracks unique request identities,
 * and manages connection timeouts and cleanup safely.
 */
object WifiConnectionManager {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val nextRequestId = AtomicLong(0)
    private val lock = Any()

    internal data class WifiSession(
        val requestId: Long,
        val ssid: String,
        val callback: ConnectivityManager.NetworkCallback,
        var isConnected: Boolean = false,
        var timeoutRunnable: Runnable? = null
    )

    @get:Synchronized
    internal var currentSession: WifiSession? = null
        private set

    /**
     * Connects to a Wi-Fi network on Android Q (API 29)+ using WifiNetworkSpecifier.
     * Safely tears down any prior in-flight or active connection before initiating.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connect(
        context: Context,
        wifiData: WiFiNetworkData,
        onStatus: (String) -> Unit = {}
    ) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            onStatus("Connectivity manager unavailable")
            return
        }

        val requestId = nextRequestId.incrementAndGet()

        synchronized(lock) {
            // Teardown prior session safely before starting new one
            teardownCurrentSessionLocked(connectivityManager)

            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(wifiData.ssid)

            if (wifiData.isHidden) {
                specifierBuilder.setIsHiddenSsid(true)
            }

            when (wifiData.security) {
                "WPA" -> {
                    if (wifiData.password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(wifiData.password)
                    }
                }
                "WPA3" -> {
                    if (wifiData.password.isNotEmpty()) {
                        specifierBuilder.setWpa3Passphrase(wifiData.password)
                    }
                }
                "nopass" -> {
                    // Open network
                }
                else -> {
                    onStatus("Unsupported Wi-Fi security type: ${wifiData.security}")
                    return
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    handleAvailable(requestId) { msg ->
                        mainHandler.post { onStatus(msg) }
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    synchronized(lock) {
                        if (currentSession?.requestId == requestId) {
                            teardownCurrentSessionLocked(connectivityManager)
                        }
                    }
                    mainHandler.post {
                        onStatus("Could not connect to ${wifiData.ssid}")
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    synchronized(lock) {
                        if (currentSession?.requestId == requestId) {
                            teardownCurrentSessionLocked(connectivityManager)
                        }
                    }
                    mainHandler.post {
                        onStatus("Disconnected from ${wifiData.ssid}")
                    }
                }
            }

            val timeoutRunnable = Runnable {
                synchronized(lock) {
                    val session = currentSession
                    if (session != null && session.requestId == requestId && !session.isConnected) {
                        teardownCurrentSessionLocked(connectivityManager)
                        mainHandler.post {
                            onStatus("Wi-Fi connection attempt timed out")
                        }
                    }
                }
            }

            initiateSession(
                requestId = requestId,
                ssid = wifiData.ssid,
                callback = callback,
                timeoutRunnable = timeoutRunnable
            )

            // Set 30-second connection timeout
            mainHandler.postDelayed(timeoutRunnable, 30_000L)

            try {
                connectivityManager.requestNetwork(request, callback)
                onStatus("Requesting connection to ${wifiData.ssid}...")
            } catch (e: SecurityException) {
                teardownCurrentSessionLocked(connectivityManager)
                onStatus("Wi-Fi network request permission denied by system")
            } catch (e: Exception) {
                teardownCurrentSessionLocked(connectivityManager)
                onStatus("Wi-Fi connection failed: ${e.message}")
            }
        }
    }

    /**
     * Explicit lifecycle cancellation and disconnect.
     * Safely unregisters the active callback and cleans up timeout timers.
     */
    fun disconnect(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        synchronized(lock) {
            teardownCurrentSessionLocked(connectivityManager)
        }
    }

    private fun teardownCurrentSessionLocked(connectivityManager: ConnectivityManager) {
        currentSession?.let { session ->
            session.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            session.timeoutRunnable = null
            try {
                connectivityManager.unregisterNetworkCallback(session.callback)
            } catch (ignored: Exception) {
            }
        }
        currentSession = null
    }

    internal fun initiateSession(
        requestId: Long,
        ssid: String,
        callback: ConnectivityManager.NetworkCallback,
        timeoutRunnable: Runnable? = null
    ) {
        synchronized(lock) {
            currentSession = WifiSession(
                requestId = requestId,
                ssid = ssid,
                callback = callback,
                isConnected = false,
                timeoutRunnable = timeoutRunnable
            )
        }
    }

    internal fun handleAvailable(requestId: Long, onStatus: (String) -> Unit = {}): Boolean {
        synchronized(lock) {
            val session = currentSession
            if (session == null || session.requestId != requestId) {
                return false
            }
            session.isConnected = true
            session.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            session.timeoutRunnable = null
            onStatus("Connected to ${session.ssid}")
            return true
        }
    }

    internal fun handleUnavailable(requestId: Long, onStatus: (String) -> Unit = {}): Boolean {
        synchronized(lock) {
            val session = currentSession
            if (session == null || session.requestId != requestId) {
                return false
            }
            currentSession = null
            onStatus("Could not connect to ${session.ssid}")
            return true
        }
    }

    internal fun handleLost(requestId: Long, onStatus: (String) -> Unit = {}): Boolean {
        synchronized(lock) {
            val session = currentSession
            if (session == null || session.requestId != requestId) {
                return false
            }
            currentSession = null
            onStatus("Disconnected from ${session.ssid}")
            return true
        }
    }

    /** Helper for unit testing session states */
    internal fun resetForTesting() {
        synchronized(lock) {
            currentSession?.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            currentSession = null
            nextRequestId.set(0)
        }
    }
}
