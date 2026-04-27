package com.omnishare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.R
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
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isRunning) stringResource(R.string.sharing_internet) else stringResource(R.string.ready_to_share),
                fontSize = 16.sp,
                color = Color.Gray
            )

            if (isRunning) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = speed, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "${stringResource(R.string.ping_label)}: $ping", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isRunning && groupInfo != null) {
                InfoCard(label = stringResource(R.string.ssid_label), value = groupInfo?.networkName ?: "...")
                InfoCard(label = stringResource(R.string.password_label), value = groupInfo?.passphrase ?: "...")
                InfoCard(label = stringResource(R.string.proxy_label), value = "$hostIpAddress:8282")
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.config_proxy_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { if (isRunning) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) stringResource(R.string.stop_sharing) else stringResource(R.string.start_sharing))
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
