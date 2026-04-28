package com.omnishare.utils

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object OmniStateRepository {
    private val _groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
    val groupInfo = _groupInfo.asStateFlow()

    private val _hostIpAddress = MutableStateFlow("192.168.49.1")
    val hostIpAddress = _hostIpAddress.asStateFlow()

    private val _hostPort = MutableStateFlow(8282)
    val hostPort = _hostPort.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _currentSpeed = MutableStateFlow("0 KB/s")
    val currentSpeed = _currentSpeed.asStateFlow()

    private val _currentPing = MutableStateFlow("-- ms")
    val currentPing = _currentPing.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    fun updateGroupInfo(info: WifiP2pGroup?) { _groupInfo.value = info }
    fun updateHostIp(ip: String) { _hostIpAddress.value = ip }
    fun updateHostPort(port: Int) { _hostPort.value = port }
    fun updateServiceStatus(running: Boolean) { _isServiceRunning.value = running }
    fun updateSpeed(speed: String) { _currentSpeed.value = speed }
    fun updatePing(ping: String) { _currentPing.value = ping }
    fun updateDevices(devices: List<String>) { _connectedDevices.value = devices }
}
