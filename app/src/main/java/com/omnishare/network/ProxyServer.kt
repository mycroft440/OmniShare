package com.omnishare.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class ProxyServer(private val port: Int = 8282) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

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
                Log.e("ProxyServer", "Erro no servidor proxy", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) {
        clientSocket.use { client ->
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()
                
                // Ler o cabeçalho inicial
                val buffer = ByteArray(8192)
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) return
                
                val request = String(buffer, 0, bytesRead)
                val lines = request.split("\r\n")
                if (lines.isEmpty()) return
                
                val firstLine = lines[0]
                val parts = firstLine.split(" ")
                if (parts.size < 2) return
                
                val method = parts[0]
                val url = parts[1]
                
                if (method == "CONNECT") {
                    handleHttps(url, client, output)
                } else {
                    handleHttp(method, url, request, client, output)
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "Erro ao lidar com cliente", e)
            }
        }
    }

    private suspend fun handleHttps(url: String, client: Socket, clientOutput: OutputStream) {
        val hostPort = url.split(":")
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toInt() else 443
        
        withContext(Dispatchers.IO) {
            try {
                val remoteSocket = Socket(host, port)
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOutput.flush()
                
                val remoteInput = remoteSocket.getInputStream()
                val remoteOutput = remoteSocket.getOutputStream()
                
                val clientToRemote = launch {
                    try {
                        client.getInputStream().copyTo(remoteOutput)
                    } catch (e: Exception) {}
                    remoteSocket.close()
                }
                
                val remoteToClient = launch {
                    try {
                        remoteInput.copyTo(clientOutput)
                    } catch (e: Exception) {}
                    client.close()
                }
                
                joinAll(clientToRemote, remoteToClient)
            } catch (e: Exception) {
                Log.e("ProxyServer", "Erro em HTTPS para $host", e)
            }
        }
    }

    private suspend fun handleHttp(method: String, url: String, fullRequest: String, client: Socket, clientOutput: OutputStream) {
        // Implementação simplificada: Repassa a requisição
        // Em um proxy real, precisaríamos parsear o host da URL
        // Aqui assumimos que a URL é completa (ex: http://google.com/)
        val host = if (url.startsWith("http://")) {
            url.substring(7).split("/")[0]
        } else {
            url.split("/")[0]
        }
        
        withContext(Dispatchers.IO) {
            try {
                val remoteSocket = Socket(host, 80)
                val remoteOutput = remoteSocket.getOutputStream()
                remoteOutput.write(fullRequest.toByteArray())
                remoteOutput.flush()
                
                val remoteInput = remoteSocket.getInputStream()
                remoteInput.copyTo(clientOutput)
                remoteSocket.close()
            } catch (e: Exception) {
                Log.e("ProxyServer", "Erro em HTTP para $host", e)
            }
        }
    }
}
