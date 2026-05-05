package com.omnishare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.omnishare.utils.OmniStateRepository
import java.net.InetAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onScan: () -> Unit
) {
    val isRunning by OmniStateRepository.isServiceRunning.collectAsState()
    val groupInfo by OmniStateRepository.groupInfo.collectAsState()
    val hostIp by OmniStateRepository.hostIpAddress.collectAsState()
    val hostPort by OmniStateRepository.hostPort.collectAsState()
    val speed by OmniStateRepository.currentSpeed.collectAsState()
    val ping by OmniStateRepository.currentPing.collectAsState()
    val devices by OmniStateRepository.connectedDevices.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmniShare") },
                actions = {
                    IconButton(onClick = onStatsClick) {
                        Icon(Icons.Default.BarChart, contentDescription = "Estatísticas")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isRunning) onStop() else onStart() },
                containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Parar" else "Iniciar"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRunning && groupInfo != null) {
                StatusCard(speed, ping, devices.size)
                Spacer(Modifier.height(16.dp))
                ConnectionInfoCard(groupInfo?.networkName ?: "OmniShare", groupInfo?.passphrase ?: "", hostIp, hostPort)
            } else {
                EmptyState(onScan)
            }
        }
    }
}

@Composable
fun StatusCard(speed: String, ping: String, deviceCount: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(Icons.Default.Speed, speed, "Velocidade")
            StatItem(Icons.Default.NetworkCheck, ping, "Latência")
            StatItem(Icons.Default.Devices, deviceCount.toString(), "Dispositivos")
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ConnectionInfoCard(ssid: String, pass: String, ip: String, port: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Dados de Conexão", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("SSID: $ssid")
            Text("Senha: $pass")
            Text("Proxy: $ip:$port")
        }
    }
}

@Composable
fun EmptyState(onScan: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("OmniShare está inativo", style = MaterialTheme.typography.titleLarge)
        Text("Inicie o compartilhamento para começar", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Conectar via QR Code")
        }
    }
}