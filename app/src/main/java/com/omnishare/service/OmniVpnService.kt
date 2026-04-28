package com.omnishare.service

import android.content.Intent
import android.net.VpnService
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.omnishare.utils.OmniLogger
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.util.concurrent.atomic.AtomicBoolean

class OmniVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)

    companion object {
        var proxyHost = "192.168.49.1"
        var proxyPort = 8282
        val isVpnActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.getAndSet(true)) return
        
        val builder = Builder()
            .setSession("OmniShare VPN")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .setBlocking(true)
            
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            OmniLogger.w("OmniVpnService", "Não foi possível excluir o app da VPN: ${e.message}")
        }
            
        vpnInterface = builder.establish()
        isVpnActive.value = true

        val notification = android.app.Notification.Builder(this, "OmniShareChannel")
            .setContentTitle("OmniShare VPN")
            .setContentText("Redirecionando tráfego...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(2, notification)
        }

        OmniLogger.i("OmniVpnService", "VPN iniciada. Redirecionando tráfego para $proxyHost:$proxyPort")

        serviceScope.launch {
            runVpnLoop()
        }
    }

    private suspend fun runVpnLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(16384)

        try {
            while (isRunning.get()) {
                val read = inputStream.read(buffer.array())
                if (read > 0) {
                    val data = buffer.array()
                    // Análise básica IPv4
                    val version = (data[0].toInt() shr 4) and 0x0F
                    if (version == 4) {
                        val protocol = data[9].toInt()
                        val destIp = InetAddress.getByAddress(data.copyOfRange(16, 20))
                        
                        // Log de detecção de tráfego
                        if (protocol == 6) { // TCP
                            val destPort = ((data[22].toInt() and 0xff) shl 8) or (data[23].toInt() and 0xff)
                            OmniLogger.d("OmniVpnService", "Capturado TCP para ${destIp.hostAddress}:$destPort")
                            // TODO: Encaminhar para tunnel SOCKS5
                        } else if (protocol == 17) { // UDP
                            val destPort = ((data[22].toInt() and 0xff) shl 8) or (data[23].toInt() and 0xff)
                            OmniLogger.d("OmniVpnService", "Capturado UDP para ${destIp.hostAddress}:$destPort")
                            // TODO: Encaminhar para tunnel SOCKS5 UDP
                        }
                    }
                }
                delay(5)
            }
        } catch (e: Exception) {
            OmniLogger.e("OmniVpnService", "Erro no loop da VPN", e)
        } finally {
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        vpnInterface?.close()
        vpnInterface = null
        isVpnActive.value = false
        stopSelf()
        OmniLogger.i("OmniVpnService", "VPN parada.")
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
