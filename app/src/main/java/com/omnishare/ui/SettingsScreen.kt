package com.omnishare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val autoRestartMax by prefs.autoRestartMax.collectAsState(5)
    val autoRestartNetwork by prefs.autoRestartNetworkChange.collectAsState(true)
    val showSpeed by prefs.showSpeed.collectAsState(true)
    val showPing by prefs.showPing.collectAsState(true)
    val maxConnections by prefs.maxConnections.collectAsState(10)
    val bannedIps by prefs.bannedIps.collectAsState(emptySet())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Text("Automação", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                SettingsSlider(
                    label = "Máximo de reinícios automáticos",
                    value = autoRestartMax.toFloat(),
                    range = 0f..10f,
                    steps = 9,
                    onValueChange = { scope.launch { prefs.updateSettings(maxRestart = it.toInt()) } }
                )
                SettingsSwitch(
                    label = "Reiniciar quando a internet voltar",
                    checked = autoRestartNetwork,
                    onCheckedChange = { scope.launch { prefs.updateSettings(networkRestart = it) } }
                )
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text("Notificações", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                SettingsSwitch(
                    label = "Mostrar velocidade (KB/s)",
                    checked = showSpeed,
                    onCheckedChange = { scope.launch { prefs.updateSettings(speed = it) } }
                )
                SettingsSwitch(
                    label = "Mostrar Ping (ms)",
                    checked = showPing,
                    onCheckedChange = { scope.launch { prefs.updateSettings(ping = it) } }
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Controle de Acesso", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                SettingsSlider(
                    label = "Limite de conexões simultâneas",
                    value = maxConnections.toFloat(),
                    range = 1f..50f,
                    steps = 49,
                    onValueChange = { scope.launch { prefs.updateSettings(maxConn = it.toInt()) } }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Dispositivos Banidos", fontSize = 14.sp, color = Color.Gray)
            }

            items(bannedIps.toList()) { ip ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(ip)
                    IconButton(onClick = { scope.launch { prefs.unbanIp(ip) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Desbanir", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("$label: ${value.toInt()}", fontSize = 14.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
