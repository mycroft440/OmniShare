package com.omnishare.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class ProxyServer(private val port: Int = 8282) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    // NUCLEAR OPTION DEPURACAO
    private val nuclearLogCounter = AtomicInteger(0)
    private val maxNuclearLogs = 5

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("ProxyServer", "Servidor Proxy iniciado na porta $port")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "Erro critico no loop do proxy", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) {
        val attemptId = nuclearLogCounter.incrementAndGet()
        val isNuclear = attemptId <= maxNuclearLogs

        clientSocket.use { client ->
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()
                
                val buffer = ByteArray(8192)
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) return
                
                val requestHeader = String(buffer, 0, bytesRead)
                val lines = requestHeader.split("\r\n")
                if (lines.isEmpty()) return
                
                val firstLine = lines[0]
                val parts = firstLine.split(" ")
                if (parts.size < 2) return
                
                val method = parts[0]
                var url = parts[1]
                
                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "--- INICIO TENTATIVA #$attemptId ---")
                    Log.e("NUCLEAR_OPTION", "Method: $method | URL: $url")
                    Log.e("NUCLEAR_OPTION", "Headers Parciais: \n${requestHeader.take(200)}")
                }

                // Extracao do Host
                var host = ""
                var remotePort = 80

                if (method == "CONNECT") {
                    val hostPort = url.split(":")
                    host = hostPort[0]
                    remotePort = if (hostPort.size > 1) hostPort[1].toInt() else 443
                } else {
                    // Tratar request HTTP convencional
                    // Precisamos achar o header 'Host:'
                    val hostLine = lines.find { it.startsWith("Host: ", ignoreCase = true) }
                    if (hostLine != null) {
                        val hostValue = hostLine.substring(6).trim()
                        val hp = hostValue.split(":")
                        host = hp[0]
                        remotePort = if (hp.size > 1) hp[1].toInt() else 80
                    } else {
                        // Tentar extrair da URL
                        if (url.startsWith("http://")) {
                            url = url.substring(7)
                        }
                        val hp = url.split("/")[0].split(":")
                        host = hp[0]
                        remotePort = if (hp.size > 1) hp[1].toInt() else 80
                    }
                }

                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "Alvo roteado -> Host: $host | Porta: $remotePort")
                }

                tunnelTraffic(host, remotePort, method, buffer, bytesRead, client, input, output, isNuclear, attemptId)

            } catch (e: Exception) {
                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "FALHA CRITICA NA TENTATIVA #$attemptId: ${e.message}", e)
                }
            } finally {
                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "--- FIM TENTATIVA #$attemptId ---")
                }
            }
        }
    }

    private suspend fun tunnelTraffic(
        host: String, port: Int, method: String, initialBuffer: ByteArray, 
        initialBytesRead: Int, client: Socket, clientInput: InputStream, 
        clientOutput: OutputStream, isNuclear: Boolean, attemptId: Int
    ) {
        withContext(Dispatchers.IO) {
            var remoteSocket: Socket? = null
            try {
                remoteSocket = Socket(host, port)
                val remoteInput = remoteSocket.getInputStream()
                val remoteOutput = remoteSocket.getOutputStream()
                
                if (method == "CONNECT") {
                    clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    clientOutput.flush()
                } else {
                    remoteOutput.write(initialBuffer, 0, initialBytesRead)
                    remoteOutput.flush()
                }
                
                // Pipeline Bidirecional Assincrono
                val clientToRemote = launch {
                    try {
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (clientInput.read(buffer).also { read = it } != -1) {
                            remoteOutput.write(buffer, 0, read)
                            remoteOutput.flush()
                        }
                    } catch (e: Exception) {}
                    remoteSocket.close() // Close remote if client dies
                }
                
                val remoteToClient = launch {
                    try {
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (remoteInput.read(buffer).also { read = it } != -1) {
                            clientOutput.write(buffer, 0, read)
                            clientOutput.flush()
                        }
                    } catch (e: Exception) {}
                    client.close() // Close client if remote dies
                }
                
                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "Tentativa #$attemptId conectada com sucesso a $host:$port. Tunel ativo.")
                }

                joinAll(clientToRemote, remoteToClient)
                
            } catch (e: Exception) {
                if (isNuclear) {
                    Log.e("NUCLEAR_OPTION", "Erro ao estabelecer tunel com $host:$port na tentativa #$attemptId", e)
                }
            } finally {
                remoteSocket?.close()
            }
        }
    }
}
