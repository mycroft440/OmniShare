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
    private val udpSessions = ConcurrentHashMap<String, Long>() // Para expirar sessões UDP inativas
    
    val connectedClientsCount: Int get() = activeClients.size
    val connectedClientsIps: List<String> get() = activeClients.keys().toList()
    
    var actualPort: Int = 8282
        private set

    fun start(prefs: AppPreferences) {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                actualPort = findFreePort(8282)
                serverSocket = ServerSocket(actualPort).apply {
                    soTimeout = 0 // Aceitar conexões infinitamente
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
        try {
            val maxConn = prefs.maxConnections.first()
            val banned = prefs.bannedIps.first()
            val clientIp = clientSocket.inetAddress.hostAddress ?: "unknown"

            if (currentConnections.get() >= maxConn || banned.contains(clientIp)) {
                OmniLogger.w("ProxyServer", "Bloqueado: $clientIp (Limite ou Ban)")
                try { clientSocket.close() } catch (e: Exception) {}
                return
            }

            currentConnections.incrementAndGet()
            activeClients[clientIp] = (activeClients[clientIp] ?: 0) + 1
            
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // Correção 1.1: Leitura rigorosa do primeiro byte ou handshake
            val firstByte = input.read()
            if (firstByte == -1) return
            
            if (firstByte == 0x05) {
                OmniLogger.d("ProxyServer", "SOCKS5 de $clientIp")
                handleSocks5(clientSocket, input, output)
            } else {
                handleHttp(clientSocket, input, output, firstByte)
            }
        } catch (e: Exception) {
            OmniLogger.e("ProxyServer", "Erro no cliente: ${e.message}")
        } finally {
            val clientIp = clientSocket.inetAddress.hostAddress ?: "unknown"
            val count = (activeClients[clientIp] ?: 1) - 1
            if (count <= 0) activeClients.remove(clientIp) else activeClients[clientIp] = count
            currentConnections.decrementAndGet()
            try { clientSocket.close() } catch (e: Exception) {}
        }
    }

    private suspend fun handleSocks5(client: Socket, input: InputStream, output: OutputStream) {
        val nMethods = input.read()
        input.skip(nMethods.toLong())
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        val header = ByteArray(4)
        input.read(header)
        val command = header[1].toInt()
        val addrType = header[3].toInt()

        var host = ""
        if (addrType == 0x01) {
            val addr = ByteArray(4)
            input.read(addr)
            host = addr.joinToString(".") { (it.toInt() and 0xff).toString() }
        } else if (addrType == 0x03) {
            val len = input.read()
            val addr = ByteArray(len)
            input.read(addr)
            host = String(addr)
        }

        val portBytes = ByteArray(2)
        input.read(portBytes)
        val remotePort = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)

        if (command == 0x01) { // CONNECT
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
            tunnelRaw(host, remotePort, client, input, output)
        } else if (command == 0x03) { // UDP ASSOCIATE
            startUdpRelay(client, output)
        }
    }

    private suspend fun startUdpRelay(client: Socket, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val udpSocket = DatagramSocket(0)
            val localPort = udpSocket.localPort
            val clientAddress = client.inetAddress
            
            OmniLogger.d("ProxyServer", "UDP Relay iniciado na porta $localPort para ${clientAddress.hostAddress}")
            
            // Resposta SOCKS5 com a porta UDP do relay
            val resp = ByteArray(10)
            resp[0] = 0x05; resp[1] = 0x00; resp[2] = 0x00; resp[3] = 0x01
            // Descobrir IP dinamicamente
            val serverIp = getP2pIp() ?: InetAddress.getByName("192.168.49.1")
            System.arraycopy(serverIp.address, 0, resp, 4, 4)
            resp[8] = (localPort shr 8).toByte()
            resp[9] = (localPort and 0xff).toByte()
            output.write(resp)
            output.flush()

            val relayJob = launch {
                val buffer = ByteArray(65535)
                var clientPort = 0
                try {
                    while (isActive && !client.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.soTimeout = 5000 // Poll cada 5s para checar isActive
                        try {
                            udpSocket.receive(packet)
                        } catch (e: Exception) { continue }
                        
                        // Limpeza periódica do mapa UDP (Fix 3.2)
                        if (System.currentTimeMillis() % 10 == 0L) {
                            val now = System.currentTimeMillis()
                            udpSessions.entries.removeIf { now - it.value > 60000 }
                        }

                        if (packet.address == clientAddress) {
                            clientPort = packet.port
                            val data = packet.data
                            if (data[2].toInt() != 0) continue // Não suportamos fragmentação

                            val atyp = data[3].toInt()
                            var offset = 4
                            val targetHost = when(atyp) {
                                0x01 -> { // IPv4
                                    val addr = InetAddress.getByAddress(data.copyOfRange(offset, offset + 4))
                                    offset += 4
                                    addr
                                }
                                0x03 -> { // Domain
                                    val len = data[offset].toInt()
                                    offset += 1
                                    val addr = InetAddress.getByName(String(data.copyOfRange(offset, offset + len)))
                                    offset += len
                                    addr
                                }
                                else -> null
                            }

                            if (targetHost != null) {
                                val targetPort = ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
                                offset += 2
                                
                                val payload = data.copyOfRange(offset, packet.length)
                                val outPacket = DatagramPacket(payload, payload.size, targetHost, targetPort)
                                udpSocket.send(outPacket)
                            }
                        } else {
                            // Se o pacote vem do destino real (desencapsulado), encapsular e mandar pro cliente
                            // [RSV 2][FRAG 1][ATYP 1][DST.ADDR 4][DST.PORT 2][DATA]
                            val responseHeader = ByteArray(10)
                            responseHeader[0] = 0; responseHeader[1] = 0; responseHeader[2] = 0; responseHeader[3] = 1
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
            
            // Mantem a conexao de controle aberta
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

    private suspend fun handleHttp(client: Socket, input: InputStream, output: OutputStream, firstByte: Int) {
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
            host = hp[0]; port = if (hp.size > 1) hp[1].toInt() else 443
        } else {
            val hostLine = lines.find { it.startsWith("Host: ", ignoreCase = true) }
            if (hostLine != null) {
                val hv = hostLine.substring(6).trim().split(":")
                host = hv[0]; port = if (hv.size > 1) hv[1].toInt() else 80
            }
        }
        if (host.isNotEmpty()) {
            val maskedHost = if (host.length > 4) "***" + host.substring(host.length - 4) else "***"
            OmniLogger.d("ProxyServer", "HTTP $method para $maskedHost:$port")
            tunnelHttp(host, port, method, header, client, input, output)
        }
    }

    private fun readHeader(input: InputStream, firstByte: Int): String? {
        val bos = java.io.ByteArrayOutputStream()
        bos.write(firstByte)
        val buffer = ByteArray(2048) // Fix 2.2: Lendo em chunks
        try {
            val bytesRead = input.read(buffer)
            if (bytesRead != -1) {
                bos.write(buffer, 0, bytesRead)
            }
            // Verifica se o cabeçalho está completo ou se precisamos ler mais
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

    private suspend fun tunnelRaw(host: String, port: Int, client: Socket, input: InputStream, output: OutputStream) {
        withContext(Dispatchers.IO) {
            try {
                Socket(host, port).use { remote ->
                    val rIn = remote.getInputStream(); val rOut = remote.getOutputStream()
                    val c2r = launch { 
                        try { input.copyTo(rOut, 65536) } catch (e: Exception) {} 
                        finally { try { remote.shutdownOutput() } catch (e: Exception) {} }
                    }
                    val r2c = launch { 
                        try { rIn.copyTo(output, 65536) } catch (e: Exception) {} 
                        finally { try { client.shutdownOutput() } catch (e: Exception) {} }
                    }
                    joinAll(c2r, r2c)
                    OmniLogger.d("ProxyServer", "Conexão encerrada com $host:$port")
                }
            } catch (e: Exception) {}
        }
    }

    private suspend fun tunnelHttp(host: String, port: Int, method: String, header: String, client: Socket, input: InputStream, output: OutputStream) {
        withContext(Dispatchers.IO) {
            try {
                Socket(host, port).use { remote ->
                    val rIn = remote.getInputStream(); val rOut = remote.getOutputStream()
                    if (method == "CONNECT") {
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()); output.flush()
                    } else {
                        rOut.write(header.toByteArray()); rOut.flush()
                    }
                    val c2r = launch { 
                        try { input.copyTo(rOut, 65536) } catch (e: Exception) {} 
                        finally { try { remote.shutdownOutput() } catch (e: Exception) {} }
                    }
                    val r2c = launch { 
                        try { rIn.copyTo(output, 65536) } catch (e: Exception) {} 
                        finally { try { client.shutdownOutput() } catch (e: Exception) {} }
                    }
                    joinAll(c2r, r2c)
                    OmniLogger.d("ProxyServer", "Conexão encerrada com $host:$port")
                }
            } catch (e: Exception) {}
        }
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
