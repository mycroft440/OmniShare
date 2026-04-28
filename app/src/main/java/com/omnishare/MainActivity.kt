package com.omnishare

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.runtime.*
import com.omnishare.service.OmniShareService
import com.omnishare.service.OmniVpnService
import com.omnishare.ui.MainScreen
import com.omnishare.ui.SettingsScreen
import com.omnishare.ui.OmniShareTheme
import com.omnishare.utils.OmniLogger

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            processQrCode(result.contents)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmniLogger.init(this)
        prefs = AppPreferences(this)
        
        requestPermissions()

        setContent {
            var currentScreen by remember { mutableStateOf("main") }

            OmniShareTheme {
                if (currentScreen == "main") {
                        MainScreen(
                            onStart = { startShareService() },
                            onStop = { stopShareService() },
                            onSettingsClick = { currentScreen = "settings" },
                            onStartVpn = { prepareVpn() },
                            onStopVpn = { stopVpnService() },
                            onScan = { launchScanner() }
                        )
                } else {
                    SettingsScreen(
                        prefs = prefs,
                        onBatteryRequest = { checkAndRequestBatteryOptimizations() },
                        onBack = { currentScreen = "main" }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startShareService() {
        val intent = Intent(this, OmniShareService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopShareService() {
        stopService(Intent(this, OmniShareService::class.java))
    }

    fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        startService(Intent(this, OmniVpnService::class.java))
    }

    fun stopVpnService() {
        startService(Intent(this, OmniVpnService::class.java).apply { action = "STOP" })
    }

    fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_button))
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun processQrCode(content: String) {
        // Exemplo: WIFI:S:DIRECT-OmniShare;T:WPA;P:12345678;H:false;;PROXY:192.168.49.1:8282
        try {
            if (content.contains("PROXY:")) {
                val proxyPart = content.substringAfter("PROXY:")
                if (proxyPart.contains(":")) {
                    val parts = proxyPart.split(":")
                    val host = parts[0]
                    val port = parts.getOrNull(1)?.toIntOrNull()
                    
                    if (host.isNotEmpty() && port != null) {
                        OmniVpnService.proxyHost = host
                        OmniVpnService.proxyPort = port
                        OmniLogger.i("MainActivity", "Configuração via QR recebida: ${OmniVpnService.proxyHost}:${OmniVpnService.proxyPort}")
                        prepareVpn()
                    } else {
                        Toast.makeText(this, "QR Code Inválido: Dados de conexão ausentes", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "QR Code Inválido: Formato incorreto", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            OmniLogger.e("MainActivity", "Erro ao processar QR Code", e)
        }
    }

    private fun checkAndRequestBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
