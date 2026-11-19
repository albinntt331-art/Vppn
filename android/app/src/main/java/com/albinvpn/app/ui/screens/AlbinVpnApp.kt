package com.albinvpn.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albinvpn.app.data.*
import com.albinvpn.app.ui.AlbinVpnViewModel
import com.albinvpn.app.vpn.AlbinVpnService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000L -> "%.1f MB/s".format(bytesPerSec / 1_000_000f)
    bytesPerSec >= 1_000L -> "%.0f KB/s".format(bytesPerSec / 1_000f)
    else -> "$bytesPerSec B/s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000f)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000f)
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbinVpnApp(vm: AlbinVpnViewModel, onToggleVpn: () -> Unit) {
    var currentTab by remember { mutableIntStateOf(0) }
    val settings by vm.settings.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selectedId by vm.selectedProfileId.collectAsState()
    val connState by vm.connectionState.collectAsState()
    val stats by vm.trafficStats.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val installedApps by vm.installedApps.collectAsState()
    val logs by vm.logs.collectAsState()

    val str = remember(settings.language) { stringsFor(settings.language) }
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }
    val isConnected = connState == ConnectionState.CONNECTED

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Living Animated Aurora Mesh Background
        if (settings.auroraEnabled) {
            AuroraBackground(
                primary = MaterialTheme.colorScheme.primary,
                tertiary = MaterialTheme.colorScheme.tertiary,
                boost = isConnected
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Albin-vpn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                        }
                    },
                    actions = {
                        // Live Ping Pill in Top Bar when connected!
                        AnimatedVisibility(visible = isConnected) {
                            val pingColor = when (val ms = stats.livePingMs) {
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                                in 0..90 -> Color(0xFF69F0AE)
                                in 91..220 -> Color(0xFFFFD54F)
                                else -> Color(0xFFFF5252)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, pingColor.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(pingColor))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stats.livePingMs?.let { "$it ms" } ?: "…",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pingColor
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Rounded.Shield, null) }, label = { Text(str.navHome) })
                    NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Rounded.Public, null) }, label = { Text(str.navServers) })
                    NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Rounded.Apps, null) }, label = { Text(str.navApps) })
                    NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Rounded.Tune, null) }, label = { Text(str.navSettings) })
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240)))
                        .togetherWith(fadeOut(tween(150)))
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
                label = "screenSwitch"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        str = str, connState = connState, stats = stats, selectedId = selectedId,
                        activeProfile = selectedProfile, settings = settings,
                        onToggleVpn = onToggleVpn, onOpenServers = { currentTab = 1 },
                        onModeChange = { m -> vm.updateSettings { it.copy(vpnMode = m) } },
                        onCheckIp = vm::checkExitIp
                    )
                    1 -> ServersScreen(
                        str = str, profiles = profiles, subscriptions = subs, selectedId = selectedId,
                        onSelect = vm::selectProfile, onTogglePriority = vm::togglePriority,
                        onDelete = vm::deleteProfile, onImportRaw = vm::importRawKeys,
                        onAddSub = vm::addSubscription, onRefreshSub = vm::refreshSubscription,
                        onPingAll = vm::pingAllProfiles, onSortByPing = vm::sortProfilesByPing
                    )
                    2 -> AppsRoutingScreen(
                        str = str, settings = settings, apps = installedApps,
                        onToggleApp = vm::toggleAppSelected,
                        onClearAll = vm::clearSelectedApps,
                        onPolicyChange = { p -> vm.updateSettings { it.copy(perAppPolicy = p) } }
                    )
                    3 -> SettingsScreen(str = str, settings = settings, logs = logs, onUpdate = vm::updateSettings)
                }
            }
        }
    }
}

@Composable
private fun AuroraBackground(primary: Color, tertiary: Color, boost: Boolean) {
    val inf = rememberInfiniteTransition(label = "aurora")
    val t by inf.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "auroraPhase"
    )
    val intensity by animateFloatAsState(if (boost) 0.36f else 0.17f, tween(900), label = "auroraBoost")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c1 = Offset(w * (0.5f + 0.25f * cos(t)), h * (0.28f + 0.12f * sin(t)))
        val c2 = Offset(w * (0.5f - 0.28f * sin(t * 0.8f)), h * (0.62f + 0.14f * cos(t * 0.8f)))
        drawCircle(
            brush = Brush.radialGradient(listOf(primary.copy(alpha = intensity), Color.Transparent), center = c1, radius = w * 0.85f),
            center = c1, radius = w * 0.85f
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(tertiary.copy(alpha = intensity * 0.72f), Color.Transparent), center = c2, radius = w * 0.75f),
            center = c2, radius = w * 0.75f
        )
    }
}

@Composable
fun HomeScreen(
    str: UiStrings,
    connState: ConnectionState,
    stats: TrafficStats,
    selectedId: String,
    activeProfile: VpnProfile?,
    settings: AppSettings,
    onToggleVpn: () -> Unit,
    onOpenServers: () -> Unit,
    onModeChange: (VpnMode) -> Unit,
    onCheckIp: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isConnected = connState == ConnectionState.CONNECTED
    val isConnecting = connState == ConnectionState.CONNECTING
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    val inf = rememberInfiniteTransition(label = "quantumOrb")
    val orbit1 by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isConnecting) 1200 else 4800, easing = LinearEasing)), label = "o1")
    val orbit2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(if (isConnecting) 1800 else 7600, easing = LinearEasing)), label = "o2")
    val ripple1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing)), label = "r1")
    val ripple2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, delayMillis = 1300, easing = FastOutSlowInEasing)), label = "r2")

    // Expressive Google Material 3 shape morph (squircle -> soft orb when connected)
    val cornerRadius by animateDpAsState(if (isConnected) 82.dp else 44.dp, spring(dampingRatio = 0.6f, stiffness = 220f), label = "morph")
    val orbScale by animateFloatAsState(if (isConnected) 1.04f else 1.0f, spring(dampingRatio = 0.55f), label = "orbScale")

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 2-Mode Google Segmented Pill: TUN vs Proxy Apps
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.vpnMode == VpnMode.TUN,
                onClick = { onModeChange(VpnMode.TUN) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Rounded.VpnLock, null, Modifier.size(18.dp)) }
            ) { Text(str.modeTun, maxLines = 1) }
            SegmentedButton(
                selected = settings.vpnMode == VpnMode.PROXY_APPS,
                onClick = { onModeChange(VpnMode.PROXY_APPS) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)) }
            ) { Text(str.modeProxyApps, maxLines = 1) }
        }

        // Quantum Multi-Ring Animated Power Core
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(252.dp)) {
                // Sonar ripples + counter-rotating orbital arcs
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = center
                    if (isConnected || isConnecting) {
                        listOf(ripple1, ripple2).forEach { p ->
                            val r = size.minDimension * (0.34f + 0.17f * p)
                            drawCircle(
                                color = primary.copy(alpha = (1f - p) * 0.35f),
                                radius = r,
                                center = c,
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }
                    rotate(orbit1) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(primary, Color.Transparent, tertiary, Color.Transparent, primary)),
                            radius = size.minDimension * 0.39f,
                            center = c,
                            style = Stroke(width = if (isConnected || isConnecting) 5f else 2.5f, cap = StrokeCap.Round)
                        )
                    }
                    rotate(orbit2) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, tertiary.copy(alpha = 0.7f), Color.Transparent, primary.copy(alpha = 0.7f))),
                            radius = size.minDimension * 0.43f,
                            center = c,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Morphing Interactive Core Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleVpn()
                    },
                    shape = RoundedCornerShape(cornerRadius),
                    color = Color.Transparent,
                    shadowElevation = if (isConnected) 18.dp else 6.dp,
                    modifier = Modifier
                        .size(166.dp)
                        .scale(orbScale)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(
                            if (isConnected) Brush.linearGradient(listOf(primary, tertiary, primaryContainer))
                            else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer))
                        )
                        .border(1.5.dp, primary.copy(alpha = if (isConnected) 0.8f else 0.35f), RoundedCornerShape(cornerRadius))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val fg = if (isConnected) MaterialTheme.colorScheme.onPrimary else primary
                        Icon(Icons.Rounded.PowerSettingsNew, null, tint = fg, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(6.dp))
                        AnimatedContent(targetState = connState, label = "st") { st ->
                            Text(
                                text = when (st) {
                                    ConnectionState.DISCONNECTED -> str.connect
                                    ConnectionState.CONNECTING -> str.connecting
                                    ConnectionState.CONNECTED -> str.connected
                                    ConnectionState.ERROR -> str.error
                                },
                                color = fg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }
                        if (isConnected) {
                            val h = stats.connectedSeconds / 3600
                            val m = (stats.connectedSeconds % 3600) / 60
                            val s = stats.connectedSeconds % 60
                            Text("%02d:%02d:%02d".format(h, m, s), color = fg.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Status + Exit IP Check Pill
            if (stats.errorMessage != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(stats.errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onCheckIp,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TravelExplore, null, modifier = Modifier.size(15.dp), tint = primary)
                            Spacer(Modifier.width(6.dp))
                            Text(stats.exitIpInfo ?: str.checkIp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (isConnected) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "↓ ${formatBytes(stats.totalRxBytes)}  ↑ ${formatBytes(stats.totalTxBytes)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Real Live Speed Sparklines (KB/s & MB/s)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficCard(str.download, formatRate(stats.rxBytesPerSec), stats.rxHistory, primary, Modifier.weight(1f))
            TrafficCard(str.upload, formatRate(stats.txBytesPerSec), stats.txHistory, tertiary, Modifier.weight(1f))
        }

        // Active Server Card
        Surface(
            onClick = onOpenServers,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (isConnected) primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (selectedId == AUTO_PROFILE_ID) Icons.Rounded.AutoAwesome else Icons.Rounded.Public, null, tint = primary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected && stats.activeServerName.isNotBlank()) stats.activeServerName
                        else if (selectedId == AUTO_PROFILE_ID) str.autoServerTitle
                        else activeProfile?.name ?: str.autoServerTitle,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedId == AUTO_PROFILE_ID) str.autoServerDesc
                        else activeProfile?.let { "${it.protocol.uppercase()} • ${it.address}:${it.port}" } ?: str.noServersYet,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrafficCard(title: String, value: String, points: List<Float>, accent: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accent)
            Spacer(Modifier.height(6.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
                val step = if (points.size > 1) size.width / (points.size - 1) else size.width
                val line = Path(); val fill = Path()
                points.forEachIndexed { i, v ->
                    val x = i * step
                    val y = size.height - (v / maxVal) * (size.height * 0.85f) - 2f
                    if (i == 0) {
                        line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
                    } else {
                        val px = (i - 1) * step
                        val py = size.height - (points[i - 1] / maxVal) * (size.height * 0.85f) - 2f
                        val cx = (px + x) / 2f
                        line.cubicTo(cx, py, cx, y, x, y)
                        fill.cubicTo(cx, py, cx, y, x, y)
                    }
                }
                fill.lineTo(size.width, size.height); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent)))
                drawPath(line, accent, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun ServersScreen(
    str: UiStrings,
    profiles: List<VpnProfile>,
    subscriptions: List<Subscription>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onTogglePriority: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImportRaw: (String) -> Int,
    onAddSub: (String, String) -> Unit,
    onRefreshSub: (Subscription) -> Unit,
    onPingAll: () -> Unit,
    onSortByPing: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showAddSub by remember { mutableStateOf(false) }
    var showAddKey by remember { mutableStateOf(false) }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { showAddSub = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Rounded.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(str.addSub, maxLines = 1, fontSize = 12.sp)
            }
            FilledTonalButton(onClick = { showAddKey = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Rounded.VpnKey, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(str.addKey, maxLines = 1, fontSize = 12.sp)
            }
            IconButton(onClick = { clipboard.getText()?.text?.let { onImportRaw(it) } }) { Icon(Icons.Rounded.ContentPaste, str.paste) }
            IconButton(onClick = onPingAll) { Icon(Icons.Rounded.Speed, str.pingAll) }
            IconButton(onClick = onSortByPing) { Icon(Icons.Rounded.Sort, str.sortByPing) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                val isAuto = selectedId == AUTO_PROFILE_ID
                Surface(
                    onClick = { onSelect(AUTO_PROFILE_ID) },
                    shape = RoundedCornerShape(24.dp),
                    color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    border = BorderStroke(1.5.dp, if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(str.autoServerTitle, fontWeight = FontWeight.Bold)
                            Text(str.autoServerDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAuto) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (subscriptions.isNotEmpty()) {
                item { Text(str.subscriptions, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(subscriptions, key = { it.id }) { sub ->
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, fontWeight = FontWeight.Bold)
                                Text(sub.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onRefreshSub(sub) }) { Icon(Icons.Rounded.Refresh, null) }
                        }
                    }
                }
            }

            item { Text("${str.servers} (${profiles.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(profiles, key = { it.id }) { p ->
                val selected = p.id == selectedId
                Surface(
                    onClick = { onSelect(p.id) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onTogglePriority(p.id) }) {
                            Icon(
                                if (p.isPriority) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                null,
                                tint = if (p.isPriority) Color(0xFFFFCA28) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.protocol.uppercase()} • ${p.address}:${p.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        p.pingMs?.let { ms ->
                            val c = if (ms < 100) Color(0xFF69F0AE) else if (ms < 220) Color(0xFFFFD54F) else Color(0xFFFF5252)
                            Text("${ms}ms", fontWeight = FontWeight.Bold, color = c, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { onDelete(p.id) }) { Icon(Icons.Rounded.DeleteOutline, null) }
                    }
                }
            }
        }
    }

    if (showAddSub) {
        AlertDialog(
            onDismissRequest = { showAddSub = false },
            title = { Text(str.addSub) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subName, onValueChange = { subName = it }, label = { Text(str.subName) })
                    OutlinedTextField(value = subUrl, onValueChange = { subUrl = it }, label = { Text(str.subUrl) })
                }
            },
            confirmButton = { Button(onClick = { onAddSub(subName, subUrl); subName = ""; subUrl = ""; showAddSub = false }) { Text(str.add) } },
            dismissButton = { TextButton(onClick = { showAddSub = false }) { Text(str.cancel) } }
        )
    }

    if (showAddKey) {
        AlertDialog(
            onDismissRequest = { showAddKey = false },
            title = { Text(str.addKey) },
            text = { OutlinedTextField(value = keyText, onValueChange = { keyText = it }, label = { Text("vless://, vmess://, trojan://, ss://...") }, minLines = 3) },
            confirmButton = { Button(onClick = { onImportRaw(keyText); keyText = ""; showAddKey = false }) { Text(str.importBtn) } },
            dismissButton = { TextButton(onClick = { showAddKey = false }) { Text(str.cancel) } }
        )
    }
}

@Composable
fun AppsRoutingScreen(
    str: UiStrings,
    settings: AppSettings,
    apps: List<InstalledAppInfo>,
    onToggleApp: (String) -> Unit,
    onClearAll: () -> Unit,
    onPolicyChange: (PerAppPolicy) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(false) }

    val filtered = remember(apps, query, showSystem, selectedOnly, settings.selectedPackages) {
        apps.filter { app ->
            (showSystem || !app.isSystem) &&
            (!selectedOnly || app.packageName in settings.selectedPackages) &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
        }.sortedByDescending { it.packageName in settings.selectedPackages }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(str.appsRoutingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(str.appsRoutingHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.perAppPolicy == PerAppPolicy.ONLY_SELECTED,
                onClick = { onPolicyChange(PerAppPolicy.ONLY_SELECTED) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text(str.onlySelectedApps, maxLines = 1, fontSize = 12.sp) }
            SegmentedButton(
                selected = settings.perAppPolicy == PerAppPolicy.EXCLUDE_SELECTED,
                onClick = { onPolicyChange(PerAppPolicy.EXCLUDE_SELECTED) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text(str.excludeSelectedApps, maxLines = 1, fontSize = 12.sp) }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(str.searchApps) },
            singleLine = true, shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            item { FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text(str.showSystemApps) }) }
            item { FilterChip(selected = selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("${str.selectedOnlyFilter} (${settings.selectedPackages.size})") }) }
            if (settings.selectedPackages.isNotEmpty()) {
                item { AssistChip(onClick = onClearAll, label = { Text(str.clearSelection) }) }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in settings.selectedPackages
                Surface(
                    onClick = { onToggleApp(app.packageName) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Android, null) }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(checked = checked, onCheckedChange = { onToggleApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    str: UiStrings,
    settings: AppSettings,
    logs: List<String>,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(str.language, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { idx, lang ->
                    SegmentedButton(
                        selected = settings.language == lang,
                        onClick = { onUpdate { it.copy(language = lang) } },
                        shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size)
                    ) { Text(lang.nativeLabel) }
                }
            }
        }

        item {
            Text(str.appearance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf(str.themeSystem, str.themeLight, str.themeDark)
                ThemeMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onUpdate { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size)
                    ) { Text(labels[idx]) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(str.accentColor, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(AccentShade.entries) { shade ->
                    val selected = settings.accentShade == shade
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(shade.seedHex))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onBackground else Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { onUpdate { it.copy(accentShade = shade) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                }
            }
        }

        item { SettingSwitchCard(str.auroraTitle, str.auroraDesc, settings.auroraEnabled) { v -> onUpdate { it.copy(auroraEnabled = v) } } }
        item { Text(str.coreAndNetwork, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { SettingSwitchCard(str.fragmentTitle, str.fragmentDesc, settings.fragmentEnabled) { v -> onUpdate { it.copy(fragmentEnabled = v) } } }
        item { SettingSwitchCard(str.muxTitle, str.muxDesc, settings.muxEnabled) { v -> onUpdate { it.copy(muxEnabled = v) } } }
        item { SettingSwitchCard(str.sniffingTitle, str.sniffingDesc, settings.sniffingEnabled) { v -> onUpdate { it.copy(sniffingEnabled = v) } } }
        item { SettingSwitchCard(str.ipv6Title, str.ipv6Desc, settings.ipv6Enabled) { v -> onUpdate { it.copy(ipv6Enabled = v) } } }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(str.logsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { AlbinVpnService.clearLogs() }) { Text(str.clearLogs) }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    logs.takeLast(20).forEach { line -> Text(line, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
