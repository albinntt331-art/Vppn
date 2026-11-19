package com.albinvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.albinvpn.app.data.ConnectionState
import com.albinvpn.app.ui.AlbinVpnViewModel
import com.albinvpn.app.ui.screens.AlbinVpnApp
import com.albinvpn.app.ui.theme.AlbinVpnTheme
import com.albinvpn.app.vpn.AlbinVpnService

class MainActivity : ComponentActivity() {

    private val vm: AlbinVpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            AlbinVpnTheme(themeMode = settings.themeMode, accentShade = settings.accentShade) {
                AlbinVpnApp(vm = vm, onToggleVpn = { toggleVpn() })
            }
        }
    }

    private fun toggleVpn() {
        val current = vm.connectionState.value
        if (current == ConnectionState.CONNECTED || current == ConnectionState.CONNECTING) {
            startService(Intent(this, AlbinVpnService::class.java).apply { action = AlbinVpnService.ACTION_STOP })
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent) else startVpnService()
        }
    }

    private fun startVpnService() {
        startForegroundService(Intent(this, AlbinVpnService::class.java).apply {
            action = AlbinVpnService.ACTION_START
            putExtra(AlbinVpnService.EXTRA_PROFILE_ID, vm.selectedProfileId.value)
        })
    }
}
