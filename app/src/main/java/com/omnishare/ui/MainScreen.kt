package com.omnishare.ui

import androidx.compose.foundation.layout.*
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
    onStop: () -> Unit
) {
    val isRunning by OmniShareService.isServiceRunning.collectAsState()
    val groupInfo by OmniShareService.groupInfo.collectAsState()

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

        Spacer(modifier = Modifier.height(48.dp))

        if (isRunning && groupInfo != null) {
            InfoCard(
                label = "Nome da Rede (SSID)",
                value = groupInfo?.networkName ?: "..."
            )
            InfoCard(
                label = "Senha",
                value = groupInfo?.passphrase ?: "..."
            )
            InfoCard(
                label = "Proxy IP",
                value = "192.168.49.1"
            )
            InfoCard(
                label = "Porta",
                value = "8282"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Configure o Proxy Manual no dispositivo cliente com os dados acima.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { if (isRunning) onStop() else onStart() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isRunning) "PARAR COMPARTILHAMENTO" else "INICIAR COMPARTILHAMENTO")
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 12.sp, color = Color.Gray)
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
