package com.phonegallery.transfer.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val SERVICE_TYPE = "_photosync._tcp"
private const val USB_URL = "http://localhost:8080"
private const val MDNS_TIMEOUT_MS = 8_000L

class NetworkDiscovery(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)

    suspend fun discoverAll(): List<DiscoveredServer> = coroutineScope {
        val usbDeferred = async { probeUsb() }
        val wifiDeferred = async { probeMdns() }

        val usb = usbDeferred.await()
        val wifi = wifiDeferred.await()

        buildList {
            usb?.let { add(it) }
            addAll(wifi)
        }
    }

    private suspend fun probeUsb(): DiscoveredServer? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$USB_URL/ping").build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                DiscoveredServer(
                    url = USB_URL,
                    type = ConnectionType.USB,
                    displayName = "USB Cable",
                )
            } else null
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun probeMdns(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveredServer>()
        val mutex = Mutex()
        var discoveryListener: NsdManager.DiscoveryListener? = null

        withTimeoutOrNull(MDNS_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { cont ->
                val resolveExecutor = Executors.newCachedThreadPool()

                fun resolveService(serviceInfo: NsdServiceInfo) {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val ip = info.host?.hostAddress ?: return
                            val port = info.port
                            val hostname = info.attributes["name"]
                                ?.let { String(it) }
                                ?: info.serviceName
                            val server = DiscoveredServer(
                                url = "http://$ip:$port",
                                type = ConnectionType.WIFI,
                                displayName = "Wi-Fi · $ip ($hostname)",
                            )
                            resolveExecutor.execute {
                                kotlinx.coroutines.runBlocking {
                                    mutex.withLock { found.add(server) }
                                }
                            }
                        }
                    }

                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        nsdManager.resolveService(serviceInfo, resolveExecutor, resolveListener)
                    } else {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    }
                }

                val listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        resolveService(serviceInfo)
                    }
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                }
                discoveryListener = listener

                try {
                    nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Unit)
                }

                cont.invokeOnCancellation {
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                    resolveExecutor.shutdown()
                }
            }
        }

        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        found.toList()
    }
}
