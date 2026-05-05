package com.omnishare.network

import android.util.Log
import com.omnishare.AppPreferences
import com.omnishare.utils.OmniLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.net.NetworkInterface

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyServer {
    private var serverSocket: ServerSocket? = null
    
    private val proxyDispatcher = Dispatchers.IO.limitedParallelism(200)
    private val scope = CoroutineScope(proxyDispatcher + SupervisorJob())
    
    private var isRunning = false
    private var currentConnections = AtomicInteger(0)
    private val activeClients = ConcurrentHashMap<String, Int>()
    private val udpSessions = ConcurrentHashMap<String, Long>()
    private val deviceStats = ConcurrentHashMap<String, AtomicLong>()
    private var qosEnabled = false
    private var qosLimitBps = 0L
    private val blockedDomains = setOf("doubleclick.net", "google-analytics.com", "ads.google.com", "crashlytics.com")
    
    val connectedClientsCount: Int get() = activeClients.size
    val connectedClientsIps: List<String> get() = activeClients.keys().toList()
    val deviceTrafficStats: Map<String, Long> get() = deviceStats.mapValues { it.value.get() }
    
    var actualPort: Int = 8282
        private set

    fun start(prefs: AppPreferences) {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            try {
                qosEnabled = prefs.qosEnabled.first()
                qosLimitBps = prefs.qosSpeedLimit.first().toLong() * 1024L
                
                actualPort = findFreePort(8282)
                serverSocket = ServerSocket(actualPort).apply {
                    soTimeout = 0
                }
                OmniLogger.i("ProxyServer", "🚀 Servidor Dual iniciado na porta $actualPort")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch { 
                        handleClient(clientSocket, prefs) 
                    }
                }
            } catch (e: Exception) {
                OmniLogger.e("ProxyServer", "Erro fatal no proxy", e)
            }
        }
    }

    private fun findFreePort(startPort: Int): Int {
        var port = startPort
        while (port < 65535) {
            try { ServerSocket(port).use { return port } } catch (e: Exception) { port++ }
        }
        return startPort
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket, prefs: AppPreferences) {
        clientSocket.soTimeout = 30000 
        val clientIp = clientSocket.inetAddress.hostAddress ?: "unknown"
        try {
            val maxConn = prefs.maxConnections.first()
            val banned = prefs.bannedIps.first()

            if (currentConnections.get() >= maxConn || banned.contains(clientIp)) {
                OmniLogger.w("ProxyServer", "Bloqueado: $clientIp (Limite ou Ban)")
                try { clientSocket.close() } catch (e: Exception) {}
                return
            }

            currentConnections.incrementAndGet()
            activeClients[clientIp] = (activeClients[clientIp] ?: 0) + 1
            
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            val firstByte = input.read()
            if (firstByte == -1) return
            
            if (firstByte == 0x05) {
                handleSocks5(clientSocket, input, output, clientIp)
            } else {
                handleHttp(clientSocket, input, output, firstByte, clientIp)
            }
        } catch (e: Exception) {
            // Erro silencioso para evitar log spam de conexões abortadas
        } finally {
            currentConnections.decrementAndGet()
            val count = activeClients[clientIp] ?: 0
            if (count <= 1) activeClients.remove(clientIp) else activeClients[clientIp] = count - 1
            try { clientSocket.close() } catch (e: Exception) {}
        }
    }

    private suspend fun handleSocks5(client: Socket, input: InputStream, output: OutputStream, clientIp: String) {
        val nmethods = input.read()
        val methods = ByteArray(nmethods)
        input.read(methods)
        
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        val version = input.read()
        if (version != 0x05) return
        val command = input.read()
        input.read() // rsv
        val atyp = input.read()
        
        var host = ""
        when (atyp) {
            0x01 -> { // IPv4
                val addr = ByteArray(4)
                input.read(addr)
                host = InetAddress.getByAddress(addr).hostAddress
            }
            0x03 -> { // Domain name
                val len = input.read()
                val domain = ByteArray(len)
                input.read(domain)
                host = String(domain)
            }
        }
        val port = ((input.read() and 0xff) shl 8) or (input.read() and 0xff)

        if (blockedDomains.any { host.contains(it, ignoreCase = true) }) {
            OmniLogger.w("ProxyServer", "AdBlock (SOCKS5): $host bloqueado para $clientIp")
            try { client.close() } catch (e: Exception) {}
            return
        }

        if (command == 0x01) { // CONNECT
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
            tunnelRaw(host, port, client, input, output, clientIp)
        } else if (command == 0x03) { // UDP ASSOCIATE
            handleUdpAssociate(client, output, clientIp)
        }
    }

    private suspend fun handleUdpAssociate(client: Socket, output: OutputStream, clientIp: String) {
        val p2pIp = getP2pIp() ?: InetAddress.getByName("0.0.0.0")
        val udpSocket = DatagramSocket(0, p2pIp)
        val relayPort = udpSocket.localPort

        val response = byteArrayOf(0x05, 0x00, 0x00, 0x01) + p2pIp.address + 
                       byteArrayOf((relayPort shr 8).toByte(), (relayPort and 0xff).toByte())
        output.write(response)
        output.flush()

        withContext(Dispatchers.IO) {
            val relayJob = launch {
                try {
                    val buffer = ByteArray(65536)
                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        
                        val data = packet.data
                        if (data[0].toInt() == 0 && data[1].toInt() == 0) {
                            val frag = data[2].toInt()
                            if (frag != 0) continue
                            
                            val clientAddress = packet.address
                            val clientPort = packet.port
                            udpSessions[clientIp] = System.currentTimeMillis()

                            val responseHeader = ByteArray(10)
                            responseHeader[0] = 0x00; responseHeader[1] = 0x00; responseHeader[2] = 0x00
                            responseHeader[3] = 0x01
                            System.arraycopy(packet.address.address, 0, responseHeader, 4, 4)
                            responseHeader[8] = (packet.port shr 8).toByte()
                            responseHeader[9] = (packet.port and 0xff).toByte()
                            
                            val fullPayload = responseHeader + packet.data.copyOfRange(0, packet.length)
                            if (clientPort != 0) {
                                val outPacket = DatagramPacket(fullPayload, fullPayload.size, clientAddress, clientPort)
                                udpSocket.send(outPacket)
                            }
                        }
                    }
                } catch (e: Exception) {
                    OmniLogger.e("ProxyServer", "UDP Relay encerrado: ${e.message}")
                } finally {
                    udpSocket.close()
                }
            }
            
            try {
                while (isActive && !client.isClosed) {
                    if (client.getInputStream().read() == -1) break
                    delay(2000)
                }
            } finally {
                relayJob.cancel()
                udpSocket.close()
            }
        }
    }

    private suspend fun handleHttp(client: Socket, input: InputStream, output: OutputStream, firstByte: Int, clientIp: String) {
        val header = readHeader(input, firstByte) ?: return
        val lines = header.split("\r\n")
        if (lines.isEmpty()) return
        
        val firstLine = lines[0]
        val parts = firstLine.split(" ")
        if (parts.size < 2) return
        
        val method = parts[0]
        val url = parts[1]
        var host = ""
        var port = 80

        if (method == "CONNECT") {
            val hp = url.split(":")
            host = hp[0]; port = if (hp.size > 1) hp[1].toIntOrNull() ?: 443 else 443
        } else {
            val hostLine = lines.find { it.startsWith("Host: ", ignoreCase = true) }
            if (hostLine != null) {
                val hv = hostLine.substring(6).trim().split(":")
                host = hv[0]; port = if (hv.size > 1) hv[1].toIntOrNull() ?: 80 else 80
            }
        }
        if (host.isNotEmpty()) {
            if (blockedDomains.any { host.contains(it, ignoreCase = true) }) {
                OmniLogger.w("ProxyServer", "AdBlock: $host bloqueado para $clientIp")
                try { client.close() } catch (e: Exception) {}
                return
            }
            tunnelHttp(host, port, method, header, client, input, output, clientIp)
        }
    }

    private fun readHeader(input: InputStream, firstByte: Int): String? {
        val bos = java.io.ByteArrayOutputStream()
        bos.write(firstByte)
        val buffer = ByteArray(2048)
        try {
            val bytesRead = input.read(buffer)
            if (bytesRead != -1) {
                bos.write(buffer, 0, bytesRead)
            }
            var header = bos.toString("UTF-8")
            if (!header.contains("\r\n\r\n")) {
                val secondBuffer = ByteArray(2048)
                val secondRead = input.read(secondBuffer)
                if (secondRead != -1) {
                    bos.write(secondBuffer, 0, secondRead)
                    header = bos.toString("UTF-8")
                }
            }
            return header
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun tunnelRaw(host: String, port: Int, client: Socket, input: InputStream, output: OutputStream, clientIp: String) {
        withContext(Dispatchers.IO) {
            try {
                java.net.Socket().apply {
                    connect(java.net.InetSocketAddress(host, port), 15000)
                    soTimeout = 60000
                }.use { remote ->
                    val rIn = remote.getInputStream(); val rOut = remote.getOutputStream()
                    val c2r = launch { 
                        try { copyWithStats(input, rOut, clientIp) } catch (e: Exception) {} 
                        finally { try { remote.close() } catch (e: Exception) {} }
                    }
                    val r2c = launch { 
                        try { copyWithStats(rIn, output, clientIp) } catch (e: Exception) {} 
                        finally { try { client.close() } catch (e: Exception) {} }
                    }
                    joinAll(c2r, r2c)
                }
            } catch (e: Exception) {}
        }
    }

    private suspend fun tunnelHttp(host: String, port: Int, method: String, header: String, client: Socket, input: InputStream, output: OutputStream, clientIp: String) {
        withContext(Dispatchers.IO) {
            try {
                java.net.Socket().apply {
                    connect(java.net.InetSocketAddress(host, port), 15000)
                    soTimeout = 60000
                }.use { remote ->
                    val rIn = remote.getInputStream(); val rOut = remote.getOutputStream()
                    if (method == "CONNECT") {
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()); output.flush()
                    } else {
                        rOut.write(header.toByteArray()); rOut.flush()
                    }
                    val c2r = launch { 
                        try { copyWithStats(input, rOut, clientIp) } catch (e: Exception) {} 
                        finally { try { remote.close() } catch (e: Exception) {} }
                    }
                    val r2c = launch { 
                        try { copyWithStats(rIn, output, clientIp) } catch (e: Exception) {} 
                        finally { try { client.close() } catch (e: Exception) {} }
                    }
                    joinAll(c2r, r2c)
                }
            } catch (e: Exception) {}
        }
    }

    private fun copyWithStats(input: InputStream, output: OutputStream, clientIp: String) {
        val buffer = ByteArray(65536)
        try {
            while (isRunning) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                output.write(buffer, 0, bytes)
                deviceStats.getOrPut(clientIp) { AtomicLong(0) }.addAndGet(bytes.toLong())
                if (qosEnabled && qosLimitBps > 0) {
                    val sleepMs = (bytes.toLong() * 1000L) / qosLimitBps
                    if (sleepMs > 0) Thread.sleep(sleepMs)
                }
            }
        } catch (e: Exception) {}
    }

    private fun getP2pIp(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name.contains("p2p") || iface.name.contains("wlan")) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                            return addr
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
