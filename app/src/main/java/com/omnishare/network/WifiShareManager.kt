package com.omnishare.network

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WifiShareManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    suspend fun startHotspot(onSuccess: (WifiP2pGroup, String) -> Unit, onError: (Int) -> Unit) {
        val created = withTimeoutOrNull(10000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onFailure(reason: Int) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }) ?: cont.resume(false)
                } catch (e: SecurityException) {
                    Log.e("WifiShareManager", "Permissão negada ao iniciar Hotspot: ${e.message}")
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false

        if (created) {
            Log.d("WifiShareManager", "Grupo P2P criado com sucesso")
            requestGroupDetails(onSuccess)
        } else {
            Log.e("WifiShareManager", "Falha ao criar grupo ou timeout")
            onError(-1)
        }
    }

    fun stopHotspot() {
        manager?.removeGroup(channel, null)
    }

    private fun requestGroupDetails(onSuccess: (WifiP2pGroup, String) -> Unit) {
        manager?.requestGroupInfo(channel) { group ->
            if (group != null) {
                manager.requestConnectionInfo(channel) { info ->
                    val ipAddress = if (info != null && info.groupFormed && info.groupOwnerAddress != null) {
                        info.groupOwnerAddress.hostAddress ?: "192.168.49.1"
                    } else {
                        "192.168.49.1"
                    }
                    Log.d("WifiShareManager", "IP Dinâmico capturado: $ipAddress")
                    onSuccess(group, ipAddress)
                }
            }
        }
    }
}
