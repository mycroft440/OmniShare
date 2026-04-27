package com.omnishare.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.p2p.WifiP2pGroup
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omnishare.AppPreferences
import com.omnishare.network.ProxyServer
import com.omnishare.network.WifiShareManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class OmniShareService : Service() {
    private lateinit var wifiManager: WifiShareManager
    private lateinit var prefs: AppPreferences
    private val proxyServer = ProxyServer()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    
    private var restartCount = AtomicInteger(0)
    private var lastTotalBytes: Long = 0

    companion object {
        const val CHANNEL_ID = "OmniShareChannel"
        val groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
        val hostIpAddress = MutableStateFlow("192.168.49.1")
        val isServiceRunning = MutableStateFlow(false)
        val currentSpeed = MutableStateFlow("0 KB/s")
        val currentPing = MutableStateFlow("-- ms")
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = WifiShareManager(this)
        prefs = AppPreferences(this)
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Iniciando...")
        startForeground(1, notification)
        startHotspotWithRetry()
        return START_STICKY
    }

    private fun startHotspotWithRetry() {
        wifiManager.startHotspot(
            onSuccess = { group, ipAddress ->
                groupInfo.value = group
                hostIpAddress.value = ipAddress
                restartCount.set(0)
                proxyServer.start(prefs)
                isServiceRunning.value = true
                startMonitoring()
            },
            onError = {
                val max = runBlocking { prefs.autoRestartMax.first() }
                if (restartCount.incrementAndGet() <= max) {
                    Log.w("OmniShareService", "Erro no roteador. Reiniciando (${restartCount.get()}/$max)...")
                    serviceScope.launch {
                        delay(3000)
                        startHotspotWithRetry()
                    }
                } else {
                    stopSelf()
                }
            }
        )
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        lastTotalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(2000)
                val showSpeed = prefs.showSpeed.first()
                val showPing = prefs.showPing.first()
                
                var speedText = ""
                if (showSpeed) {
                    val currentTotal = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
                    val diff = (currentTotal - lastTotalBytes) / 2 // bytes per second (approx)
                    lastTotalBytes = currentTotal
                    speedText = formatSpeed(diff)
                    currentSpeed.value = speedText
                }

                var pingText = ""
                if (showPing) {
                    pingText = measurePing()
                    currentPing.value = pingText
                }

                updateNotification("Status: Ativo | $speedText | Ping: $pingText")
            }
        }
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB/s", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%d KB/s", bytes / 1024)
            else -> "$bytes B/s"
        }
    }

    private fun measurePing(): String {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1000) }
            val end = System.currentTimeMillis()
            "${end - start} ms"
        } catch (e: Exception) {
            "-- ms"
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    if (prefs.autoRestartNetworkChange.first() && isServiceRunning.value) {
                        Log.i("OmniShareService", "Internet restabelecida. Reiniciando para atualizar rotas.")
                        restartHotspot()
                    }
                }
            }
        })
    }

    private fun restartHotspot() {
        wifiManager.stopHotspot()
        proxyServer.stop()
        startHotspotWithRetry()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        wifiManager.stopHotspot()
        proxyServer.stop()
        isServiceRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "OmniShare Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniShare Monitor")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }
}
