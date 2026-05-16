package com.omnishare.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.utils.OmniStateRepository
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val devices by OmniStateRepository.connectedDevices.collectAsState()
    val traffic by OmniStateRepository.deviceTraffic.collectAsState()
    val maxTraffic = traffic.values.maxOrNull() ?: 1L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = PurpleDeep, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Estatísticas", fontWeight = FontWeight.W700, fontSize = 22.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (devices.isEmpty()) {
            // ── Animated Empty State ──
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val infiniteTransition = rememberInfiniteTransition(label = "statsEmpty")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.9f, targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(animation = tween(1800, easing = EaseInOutCubic), repeatMode = RepeatMode.Reverse),
                        label = "emptyScale"
                    )
                    Icon(
                        Icons.Default.DevicesOther, contentDescription = null,
                        modifier = Modifier.size(72.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhum dispositivo conectado", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(6.dp))
                    Text("Os dispositivos aparecerão aqui quando conectarem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // ── Summary Card ──
                item {
                    val totalBytes = traffic.values.sum()
                    SummaryCard(deviceCount = devices.size, totalBytes = totalBytes)
                }

                // ── Device Cards ──
                itemsIndexed(devices) { index, ip ->
                    val bytes = traffic[ip] ?: 0L
                    val progress = if (maxTraffic > 0) (bytes.toFloat() / maxTraffic.toFloat()) else 0f

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300, delayMillis = index * 80)) + slideInVertically(tween(300, delayMillis = index * 80)) { 50 }
                    ) {
                        DeviceCard(ip = ip, bytes = bytes, progress = progress, index = index)
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun SummaryCard(deviceCount: Int, totalBytes: Long) {
    val gradient = Brush.linearGradient(
        colors = listOf(PurpleDeep.copy(alpha = 0.15f), CyanNeon.copy(alpha = 0.1f)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(gradient).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(deviceCount.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.W700, color = PurpleDeep)
                    Text("Dispositivos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatBytes(totalBytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.W700, color = CyanNeon)
                    Text("Tráfego Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun DeviceCard(ip: String, bytes: Long, progress: Float, index: Int) {
    val avatarColors = listOf(CyanNeon, PurpleDeep, OrangeAccent, GreenSuccess, PurpleLight, CyanDark)
    val avatarColor = avatarColors[ip.hashCode().absoluteValue % avatarColors.size]

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, delayMillis = index * 100, easing = EaseOutCubic),
        label = "progressAnim"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ── Avatar ──
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(avatarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = avatarColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ip, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(formatBytes(bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                // ── Traffic Badge ──
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = avatarColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        formatBytes(bytes),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.W600,
                        color = avatarColor
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // ── Gradient Progress Bar ──
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(animatedProgress).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Brush.linearGradient(listOf(CyanNeon, PurpleDeep)))
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
