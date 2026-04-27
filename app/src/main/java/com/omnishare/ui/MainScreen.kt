package com.omnishare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.service.OmniShareService

@Composable
fun MainScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isRunning by OmniShareService.isServiceRunning.collectAsState()
    val groupInfo by OmniShareService.groupInfo.collectAsState()
    val hostIpAddress by OmniShareService.hostIpAddress.collectAsState()
    val speed by OmniShareService.currentSpeed.collectAsState()
    val ping by OmniShareService.currentPing.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Configurações")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "OmniShare",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isRunning) "Compartilhando Internet" else "Pronto para compartilhar",
                fontSize = 16.sp,
                color = Color.Gray
            )

            if (isRunning) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = speed, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Ping: $ping", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isRunning && groupInfo != null) {
                InfoCard(label = "SSID", value = groupInfo?.networkName ?: "...")
                InfoCard(label = "Senha", value = groupInfo?.passphrase ?: "...")
                InfoCard(label = "Proxy", value = "$hostIpAddress:8282")
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { if (isRunning) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "PARAR COMPARTILHAMENTO" else "INICIAR COMPARTILHAMENTO")
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 12.sp, color = Color.Gray)
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
