package com.omnishare.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.p2p.WifiP2pGroup
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omnishare.AppPreferences
import com.omnishare.network.ProxyServer
import com.omnishare.network.WifiShareManager
import com.omnishare.utils.OmniLogger
import com.omnishare.utils.OmniStateRepository
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
    private val isRunning = java.util.concurrent.atomic.AtomicBoolean(true)

    companion object {
        const val CHANNEL_ID = "OmniShareChannel"
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
        startHotspotWithRetry()
        return START_STICKY
    }

    private fun startHotspotWithRetry() {
        serviceScope.launch {
            wifiManager.startHotspot(
                onSuccess = { group, ipAddress ->
                    OmniStateRepository.updateGroupInfo(group)
                    OmniStateRepository.updateHostIp(ipAddress)
                    restartCount.set(0)
                    proxyServer.start(prefs)
                    OmniStateRepository.updateHostPort(proxyServer.actualPort)
                    OmniStateRepository.updateServiceStatus(true)
                    startMonitoring()
                },
                onError = {
                    val max = runBlocking { prefs.autoRestartMax.first() }
                    if (restartCount.incrementAndGet() <= max) {
                        OmniLogger.w("OmniShareService", "Erro no roteador. Reiniciando (${restartCount.get()}/$max)...")
                        serviceScope.launch {
                            delay(3000)
                            startHotspotWithRetry()
                        }
                    } else {
                        OmniStateRepository.updateServiceStatus(false)
                        stopSelf()
                    }
                }
            )
        }
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
                    val uid = android.os.Process.myUid()
                    val currentTotal = TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid)
                    val diff = (currentTotal - lastTotalBytes) / 2
                    lastTotalBytes = currentTotal
                    speedText = formatSpeed(diff)
                    OmniStateRepository.updateSpeed(speedText)
                }

                var pingText = ""
                if (showPing) {
                    pingText = measurePing()
                    OmniStateRepository.updatePing(pingText)
                }

                OmniStateRepository.updateDevices(proxyServer.connectedClientsIps)

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
            // Fix 2.1: Solução leve via Socket (evita fork de processo Linux)
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
                    if (prefs.autoRestartNetworkChange.first() && OmniStateRepository.isServiceRunning.value) {
                        OmniLogger.i("OmniShareService", "Internet restabelecida. Reiniciando para atualizar rotas.")
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
        isRunning.set(false)
        monitorJob?.cancel()
        serviceScope.cancel()
        
        // Correção 2.3: Fechar sockets forçadamente para liberar threads IO
        wifiManager.stopHotspot()
        proxyServer.stop()
        OmniStateRepository.updateServiceStatus(false)
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
