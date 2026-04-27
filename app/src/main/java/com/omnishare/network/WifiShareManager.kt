package com.omnishare.network

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiShareManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    fun startHotspot(onSuccess: (WifiP2pGroup, String) -> Unit, onError: (Int) -> Unit) {
        manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiShareManager", "Grupo P2P criado com sucesso")
                requestGroupDetails(onSuccess)
            }

            override fun onFailure(reason: Int) {
                Log.e("WifiShareManager", "Falha ao criar grupo: $reason")
                onError(reason)
            }
        })
    }

    fun stopHotspot() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiShareManager", "Grupo P2P removido")
            }

            override fun onFailure(reason: Int) {
                Log.e("WifiShareManager", "Falha ao remover grupo: $reason")
            }
        })
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
