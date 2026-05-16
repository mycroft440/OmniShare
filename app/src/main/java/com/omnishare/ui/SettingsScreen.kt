package com.omnishare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnishare.AppPreferences
import com.omnishare.R
import com.omnishare.network.WolManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    onBatteryRequest: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val autoRestartMax by prefs.autoRestartMax.collectAsState(5)
    val autoRestartNetwork by prefs.autoRestartNetworkChange.collectAsState(true)
    val showSpeed by prefs.showSpeed.collectAsState(true)
    val showPing by prefs.showPing.collectAsState(true)
    val maxConnections by prefs.maxConnections.collectAsState(10)
    val bannedIps by prefs.bannedIps.collectAsState(emptySet())
    val adblock by prefs.adblockEnabled.collectAsState(false)
    val qos by prefs.qosEnabled.collectAsState(false)
    val qosLimit by prefs.qosSpeedLimit.collectAsState(0)
    val wolEnabled by prefs.wolEnabled.collectAsState(false)
    var wolMac by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.W700, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── Automation Section ──
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.automation_section),
                    icon = Icons.Default.AutoMode,
                    iconColor = CyanNeon
                ) {
                    StyledSlider(
                        label = stringResource(R.string.restart_max_label),
                        value = autoRestartMax.toFloat(),
                        range = 0f..10f,
                        steps = 9,
                        onValueChange = { scope.launch { prefs.updateSettings(maxRestart = it.toInt()) } }
                    )
                    Spacer(Modifier.height(4.dp))
                    StyledSwitch(
                        label = stringResource(R.string.restart_network_label),
                        icon = Icons.Default.Refresh,
                        checked = autoRestartNetwork,
                        onCheckedChange = { scope.launch { prefs.updateSettings(networkRestart = it) } }
                    )
                }
            }

            // ── Notification Section ──
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.notification_section),
                    icon = Icons.Default.Notifications,
                    iconColor = PurpleDeep
                ) {
                    StyledSwitch(
                        label = stringResource(R.string.show_speed_label),
                        icon = Icons.Default.Speed,
                        checked = showSpeed,
                        onCheckedChange = { scope.launch { prefs.updateSettings(speed = it) } }
                    )
                    Spacer(Modifier.height(4.dp))
                    StyledSwitch(
                        label = stringResource(R.string.show_ping_label),
                        icon = Icons.Default.NetworkCheck,
                        checked = showPing,
                        onCheckedChange = { scope.launch { prefs.updateSettings(ping = it) } }
                    )
                }
            }

            // ── Access Control Section ──
            item {
                SettingsSectionCard(
                    title = stringResource(R.string.access_control_section),
                    icon = Icons.Default.Security,
                    iconColor = OrangeAccent
                ) {
                    StyledSlider(
                        label = stringResource(R.string.max_connections_label),
                        value = maxConnections.toFloat(),
                        range = 1f..50f,
                        steps = 49,
                        onValueChange = { scope.launch { prefs.updateSettings(maxConn = it.toInt()) } }
                    )
                }
            }

            // ── System Section ──
            item {
                SettingsSectionCard(
                    title = "Sistema",
                    icon = Icons.Default.PhoneAndroid,
                    iconColor = GreenSuccess
                ) {
                    Card(
                        onClick = onBatteryRequest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GreenSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.battery_optimization_label), fontWeight = FontWeight.W500, style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.battery_optimization_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            // ── Banned IPs Section ──
            if (bannedIps.isNotEmpty()) {
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.banned_devices_label),
                        icon = Icons.Default.Block,
                        iconColor = RedError
                    ) {
                        bannedIps.toList().forEach { ip ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(ip, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { scope.launch { prefs.unbanIp(ip) } }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = RedError, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun StyledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CyanNeon.copy(alpha = 0.12f)
            ) {
                Text(
                    "${value.toInt()}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    color = CyanNeon
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CyanNeon,
                activeTrackColor = CyanNeon,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun StyledSwitch(label: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyanNeon,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
