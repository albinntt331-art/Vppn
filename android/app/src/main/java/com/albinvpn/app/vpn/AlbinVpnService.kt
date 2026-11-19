package com.albinvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.TrafficStats as AndroidTrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.albinvpn.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AlbinVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.albinvpn.START"
        const val ACTION_STOP = "com.albinvpn.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val CHANNEL_ID = "albinvpn_channel"

        private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
        val state: StateFlow<ConnectionState> = _state

        private val _stats = MutableStateFlow(TrafficStats())
        val stats: StateFlow<TrafficStats> = _stats

        private val _logs = MutableStateFlow(listOf("Albin-vpn v3 готов к работе."))
        val logs: StateFlow<List<String>> = _logs

        fun appendLog(msg: String) { _logs.value = (_logs.value + msg).takeLast(250) }
        fun clearLogs() { _logs.value = listOf("Журнал очищен.") }
        fun setExitIp(info: String?) { _stats.value = _stats.value.copy(exitIpInfo = info) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeEngine: VpnCoreEngine? = null
    private var monitorJob: Job? = null
    private var pingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); stopSelf() }
            ACTION_START -> startVpn(intent.getStringExtra(EXTRA_PROFILE_ID))
        }
        return START_STICKY
    }

    private fun startVpn(requestedId: String?) {
        val repo = ProfileRepository(this)
        val allProfiles = repo.loadProfiles()
        if (allProfiles.isEmpty()) {
            val err = "Нет серверов. Добавьте подписку или ключ."
            appendLog("[VPN ERROR] $err")
            _stats.value = _stats.value.copy(errorMessage = err)
            _state.value = ConnectionState.ERROR
            return
        }

        _state.value = ConnectionState.CONNECTING
        _stats.value = _stats.value.copy(errorMessage = null, exitIpInfo = null)
        startForeground(1, buildNotification("Подключение Albin-vpn..."))

        scope.launch {
            val settings = repo.loadSettings()
            val targetProfile = if (requestedId == null || requestedId == AUTO_PROFILE_ID) {
                pickBestAutoProfile(repo, allProfiles)
            } else {
                allProfiles.firstOrNull { it.id == requestedId } ?: pickBestAutoProfile(repo, allProfiles)
            }

            if (targetProfile == null) {
                val err = "Сервер не отвечает. Проверьте ключи и интернет."
                appendLog("[VPN ERROR] $err")
                _stats.value = _stats.value.copy(errorMessage = err)
                _state.value = ConnectionState.ERROR
                stopVpn(keepError = true)
                return@launch
            }

            val displayName = if (requestedId == null || requestedId == AUTO_PROFILE_ID) {
                "Авто → ${targetProfile.name}"
            } else targetProfile.name
            appendLog("[Albin-vpn] Сервер: $displayName (${targetProfile.protocol.uppercase()})")

            val builder = Builder()
                .setSession("Albin-vpn")
                .setMtu(settings.tunMtu.coerceIn(1280, 1500))
                .addAddress("10.0.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            if (settings.ipv6Enabled) {
                builder.addAddress("fd00::2", 126)
                builder.addRoute("::", 0)
            }

            runCatching { builder.addDisallowedApplication(packageName) }

            if (settings.vpnMode == VpnMode.PROXY_APPS && settings.selectedPackages.isNotEmpty()) {
                when (settings.perAppPolicy) {
                    PerAppPolicy.ONLY_SELECTED -> {
                        settings.selectedPackages.filter { it != packageName }.forEach { pkg ->
                            runCatching { builder.addAllowedApplication(pkg) }
                        }
                        appendLog("[Mode] Proxy Apps (только выбранные: ${settings.selectedPackages.size})")
                    }
                    PerAppPolicy.EXCLUDE_SELECTED -> {
                        settings.selectedPackages.forEach { pkg ->
                            runCatching { builder.addDisallowedApplication(pkg) }
                        }
                        appendLog("[Mode] Proxy Apps (исключено: ${settings.selectedPackages.size})")
                    }
                }
            } else {
                appendLog("[Mode] TUN (Строгий — весь трафик в VPN)")
            }

            tunInterface = builder.establish()
            val tunFd = tunInterface?.fd ?: -1
            if (tunFd < 0) {
                val err = "Не удалось открыть TUN-интерфейс."
                appendLog("[VPN ERROR] $err")
                _stats.value = _stats.value.copy(errorMessage = err)
                _state.value = ConnectionState.ERROR
                stopVpn(keepError = true)
                return@launch
            }

            val configJson = ConfigBuilder.buildXrayConfig(targetProfile, settings)
            val engine = XrayCoreEngine()
            activeEngine = engine

            val started = engine.start(
                context = this@AlbinVpnService,
                configJson = configJson,
                tunFd = tunFd,
                protectFd = { fd -> protect(fd) },
                onLog = { appendLog(it) }
            )

            if (started) {
                _state.value = ConnectionState.CONNECTED
                _stats.value = _stats.value.copy(activeServerName = displayName, errorMessage = null)
                startForeground(1, buildNotification("Защищено: $displayName"))
                startTrafficAndPingMonitors(repo, targetProfile)
            } else {
                val err = "Ядро Xray не запустилось. Проверьте Logs."
                _stats.value = _stats.value.copy(errorMessage = err)
                _state.value = ConnectionState.ERROR
                stopVpn(keepError = true)
            }
        }
    }

    private suspend fun pickBestAutoProfile(repo: ProfileRepository, profiles: List<VpnProfile>): VpnProfile? = coroutineScope {
        val priority = profiles.filter { it.isPriority }
        val pool = if (priority.isNotEmpty()) priority else profiles
        val measured = pool.map { p -> async { p to repo.measureTcpPing(p.address, p.port) } }.awaitAll()
        val best = measured.filter { it.second != null }.minByOrNull { it.second!! }?.first
        if (best != null) return@coroutineScope best
        if (priority.isNotEmpty()) {
            val rest = profiles.filterNot { it.isPriority }.map { p -> async { p to repo.measureTcpPing(p.address, p.port) } }.awaitAll()
            rest.filter { it.second != null }.minByOrNull { it.second!! }?.first ?: profiles.firstOrNull()
        } else profiles.firstOrNull()
    }

    private fun startTrafficAndPingMonitors(repo: ProfileRepository, profile: VpnProfile) {
        monitorJob?.cancel()
        pingJob?.cancel()
        val startMs = System.currentTimeMillis()
        val uid = android.os.Process.myUid()
        var lastTotalRx = AndroidTrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        var lastTotalTx = AndroidTrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        var lastUidRx = AndroidTrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        var lastUidTx = AndroidTrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        var accRx = 0L
        var accTx = 0L

        monitorJob = scope.launch {
            while (isActive && _state.value == ConnectionState.CONNECTED) {
                delay(1000)
                val nowTotalRx = AndroidTrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                val nowTotalTx = AndroidTrafficStats.getTotalTxBytes().coerceAtLeast(0L)
                val nowUidRx = AndroidTrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
                val nowUidTx = AndroidTrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)

                val dUidRx = (nowUidRx - lastUidRx).coerceAtLeast(0L)
                val dUidTx = (nowUidTx - lastUidTx).coerceAtLeast(0L)
                val dTotRx = (nowTotalRx - lastTotalRx).coerceAtLeast(0L)
                val dTotTx = (nowTotalTx - lastTotalTx).coerceAtLeast(0L)

                // Use UID traffic (exact VPN socket bytes) with fallback to device total delta
                val rxBps = if (dUidRx > 0L) dUidRx else dTotRx
                val txBps = if (dUidTx > 0L) dUidTx else dTotTx
                accRx += rxBps
                accTx += txBps

                lastTotalRx = nowTotalRx; lastTotalTx = nowTotalTx
                lastUidRx = nowUidRx; lastUidTx = nowUidTx

                val cur = _stats.value
                _stats.value = cur.copy(
                    rxBytesPerSec = rxBps,
                    txBytesPerSec = txBps,
                    totalRxBytes = accRx,
                    totalTxBytes = accTx,
                    rxHistory = cur.rxHistory.drop(1) + (rxBps / 1024f),
                    txHistory = cur.txHistory.drop(1) + (txBps / 1024f),
                    connectedSeconds = (System.currentTimeMillis() - startMs) / 1000L
                )
            }
        }

        // Continuous live ping every 3 seconds (HTTP 204 latency + TCP fallback)
        pingJob = scope.launch {
            while (isActive && _state.value == ConnectionState.CONNECTED) {
                val ms = measureHttp204Ping() ?: repo.measureTcpPing(profile.address, profile.port)
                _stats.value = _stats.value.copy(livePingMs = ms)
                delay(3000)
            }
        }
    }

    private fun measureHttp204Ping(): Int? = runCatching {
        val t0 = System.currentTimeMillis()
        val conn = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 2500
        conn.requestMethod = "HEAD"
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..399) (System.currentTimeMillis() - t0).toInt() else null
    }.getOrNull()

    private fun stopVpn(keepError: Boolean = false) {
        monitorJob?.cancel()
        pingJob?.cancel()
        activeEngine?.stop()
        activeEngine = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        if (!keepError) {
            _state.value = ConnectionState.DISCONNECTED
            _stats.value = TrafficStats()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Albin-vpn", NotificationManager.IMPORTANCE_LOW))
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Albin-vpn")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
