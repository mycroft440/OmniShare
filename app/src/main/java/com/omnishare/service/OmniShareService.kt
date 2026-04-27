package com.omnishare.service

import android.app.*
import android.content.Intent
import android.net.wifi.p2p.WifiP2pGroup
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omnishare.network.ProxyServer
import com.omnishare.network.WifiShareManager
import kotlinx.coroutines.flow.MutableStateFlow

class OmniShareService : Service() {
    private lateinit var wifiManager: WifiShareManager
    private val proxyServer = ProxyServer()

    companion object {
        const val CHANNEL_ID = "OmniShareChannel"
        val groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
        val hostIpAddress = MutableStateFlow("192.168.49.1")
        val isServiceRunning = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = WifiShareManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Iniciando compartilhamento...")
        startForeground(1, notification)

        wifiManager.startHotspot(
            onSuccess = { group, ipAddress ->
                groupInfo.value = group
                hostIpAddress.value = ipAddress
                updateNotification("Rede: ${group.networkName} | IP: $ipAddress")
                proxyServer.start()
                isServiceRunning.value = true
            },
            onError = {
                stopSelf()
            }
        )

        return START_STICKY
    }

    override fun onDestroy() {
        wifiManager.stopHotspot()
        proxyServer.stop()
        isServiceRunning.value = false
        groupInfo.value = null
        hostIpAddress.value = "192.168.49.1"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "OmniShare Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniShare Ativo")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }
}
