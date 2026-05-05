package com.omnishare.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*

object WolManager {
    fun sendMagicPacket(macAddress: String, broadcastIp: String = "192.168.49.255") {
        try {
            val macBytes = getMacBytes(macAddress)
            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0..5) {
                bytes[i] = 0xff.toByte()
            }
            for (i in 6 until bytes.size step macBytes.size) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
            }

            val address = InetAddress.getByName(broadcastIp)
            val packet = DatagramPacket(bytes, bytes.size, address, 9)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMacBytes(macStr: String): ByteArray {
        val bytes = ByteArray(6)
        val hex = macStr.split(":", "-")
        if (hex.size != 6) throw IllegalArgumentException("Endereço MAC inválido")
        try {
            for (i in 0..5) {
                bytes[i] = hex[i].toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Dígitos hexadecimais inválidos no endereço MAC")
        }
        return bytes
    }
}
