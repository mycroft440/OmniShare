package com.omnishare.network

import android.os.Environment
import com.omnishare.utils.OmniLogger
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class FileServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val port = 8080

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(start = true, name = "FileServerThread") {
            try {
                serverSocket = ServerSocket(port)
                OmniLogger.i("FileServer", "Servidor de arquivos iniciado na porta $port")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    thread { handleClient(client) }
                }
            } catch (e: Exception) {
                OmniLogger.e("FileServer", "Erro no servidor de arquivos: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: return
            val path = line.split(" ")[1]
            
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val out = DataOutputStream(socket.getOutputStream())

            if (path == "/") {
                val files = root.listFiles() ?: emptyArray()
                val html = StringBuilder("<html><body><h1>OmniShare Files</h1><ul>")
                files.forEach {
                    html.append("<li><a href=\"/${it.name}\">${it.name}</a></li>")
                }
                html.append("</ul></body></html>")
                sendResponse(out, "text/html", html.toString().toByteArray())
            } else {
                val file = File(root, path.substring(1))
                if (file.exists() && file.isFile) {
                    sendResponse(out, "application/octet-stream", file.readBytes(), file.name)
                } else {
                    sendResponse(out, "text/plain", "Arquivo nao encontrado".toByteArray(), status = "404 Not Found")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    private fun sendResponse(out: DataOutputStream, type: String, data: ByteArray, fileName: String? = null, status: String = "200 OK") {
        out.writeBytes("HTTP/1.1 $status\r\n")
        out.writeBytes("Content-Type: $type\r\n")
        out.writeBytes("Content-Length: ${data.size}\r\n")
        if (fileName != null) {
            out.writeBytes("Content-Disposition: attachment; filename=\"$fileName\"\r\n")
        }
        out.writeBytes("\r\n")
        out.write(data)
        out.flush()
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
