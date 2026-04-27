package com.omnishare.network

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiShareManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    fun startHotspot(onSuccess: (WifiP2pGroup) -> Unit, onError: (Int) -> Unit) {
        manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiShareManager", "Grupo P2P criado com sucesso")
                requestGroupInfo(onSuccess)
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

    private fun requestGroupInfo(onSuccess: (WifiP2pGroup) -> Unit) {
        manager?.requestGroupInfo(channel) { group ->
            if (group != null) {
                onSuccess(group)
            }
        }
    }
}
