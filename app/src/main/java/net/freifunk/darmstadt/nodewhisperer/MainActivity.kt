@file:OptIn(ExperimentalMaterial3Api::class)

package net.freifunk.darmstadt.nodewhisperer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import net.freifunk.darmstadt.nodewhisperer.models.GluonNode
import net.freifunk.darmstadt.nodewhisperer.models.ScanResultListModel
import net.freifunk.darmstadt.nodewhisperer.models.WifiScanResult
import net.freifunk.darmstadt.nodewhisperer.models.enums.NodeStatus
import net.freifunk.darmstadt.nodewhisperer.services.CommunityService
import net.freifunk.darmstadt.nodewhisperer.services.NodeStatusService
import net.freifunk.darmstadt.nodewhisperer.services.WifiScanService
import net.freifunk.darmstadt.nodewhisperer.services.WifiScanServiceResultReceiver
import net.freifunk.darmstadt.nodewhisperer.ui.theme.LocalCustomColorsPalette
import net.freifunk.darmstadt.nodewhisperer.ui.theme.NodeWhispererTheme
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException


class MainActivity : ComponentActivity() {
    val scanResultListModel = ScanResultListModel()
    val wifiScanService = WifiScanService(this, lifecycleScope)
    val communityService = CommunityService(this)
    private val wifiScanReceiver = object : WifiScanServiceResultReceiver {
        override fun onScanResultUpdate(wifiScanResults: List<WifiScanResult>) {
            Log.d("MainActivity", "Scan results updated {${wifiScanResults.size}}")
            updateScanResultList(wifiScanResults)
        }
    }

    fun haveAllPermissions(): Boolean {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun permissionToast() {
        Toast.makeText(this, getString(R.string.msg_permission_request), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            activityDesign(this, wifiScanService, scanResultListModel)
        }

        /* Check if WiFi Permission is granted */
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            /* Do nothing. We have a Task for that */
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CHANGE_WIFI_STATE)
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_WIFI_STATE)
        }

        wifiScanService.registerReceiver(wifiScanReceiver)

        if (!haveAllPermissions()) {
            permissionToast()
        } else {
            toggleScanState(wifiScanService, scanResultListModel)
        }
    }

    override fun onPause() {
        super.onPause()
        if (wifiScanService.scanningEnabled.value) {
            wifiScanService.pauseScanning()
        }
    }

    override fun onResume() {
        super.onResume()
        if (haveAllPermissions() && wifiScanService.scanningEnabled.value && wifiScanService.scanningPaused.value) {
            wifiScanService.resumeScanning()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanService.unregisterReceiver(wifiScanReceiver)
    }

    private fun updateScanResultList(wifiScanResults: List<WifiScanResult>) {
        scanResultListModel.scanResults.removeAll {
            it.lastSeen?.time!! < System.currentTimeMillis() - 60000
        }

        /* Loop through new scan results */
        for (result in wifiScanResults) {
            if (result.getGluonElements().size == 0) {
                /* No Gluon Node VIF */
                continue
            }

            val node = GluonNode()

            try {
                node.addScanResult(result)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error adding scan result: ${e.message}")
                continue
            }

            if (node.nodeId == null || node.hostname == null)
                continue

            /* Find Node with matching NodeID in results */
            val existingNode = scanResultListModel.scanResults.find {
                it.nodeId?.macAddress.contentEquals(node.nodeId?.macAddress)
            }

            /* Add new Gluon node if not found, otherwise add scan result to existing node */
            if (existingNode == null) {
                scanResultListModel.scanResults.add(node)
            } else {
                existingNode.addScanResult(result)
            }
        }

        /* Add CommuityInformation */
        for (node in scanResultListModel.scanResults) {
            try {
                if (node.siteCode != null) {
                    node.communityInformation =
                        communityService.getCommunityInformation(node.siteCode!!.unresolved)

                    /* Update SiteCode */
                    node.siteCode!!.resolved = node.communityInformation!!.shortName

                    /* Update DomainCode */
                    if (node.domain != null &&
                        node.communityInformation!!.domainNames != null &&
                        node.communityInformation!!.domainNames!!.containsKey(node.domain!!.unresolved)
                    ) {
                        node.domain!!.resolved =
                            node.communityInformation!!.domainNames!![node.domain!!.unresolved]!!
                    }
                }
            } catch (e: FileNotFoundException) {
                Log.e("MainActivity", "Community file not found: ${e.message}")
                continue
            } catch (e: JSONException) {
                Log.e("MainActivity", "Community file not found: ${e.message}")
                continue
            }
        }
    }
    fun generateRawDebugInfo(): String {
        if (!haveAllPermissions()) {
            permissionToast()
        }
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("total_nodes", scanResultListModel.scanResults.size)
            jsonObject.put("device_manufacturer", android.os.Build.MANUFACTURER)
            jsonObject.put("device_model", android.os.Build.MODEL)
            jsonObject.put("android_version", android.os.Build.VERSION.RELEASE)
            jsonObject.put("permission_fine_location", checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)

            val nodesArray = JSONArray()
            for (node in scanResultListModel.scanResults) {
                val nodeObject = JSONObject()
                nodeObject.put("hostname", node.hostname ?: "")
                nodeObject.put("node_id", node.nodeId.toString())
                nodeObject.put("status", NodeStatusService.getNodeStatus(node).toString())
                nodeObject.put("site_code", getSiteDomainString(node) ?: "")
                nodeObject.put("last_seen", node.lastSeen?.toString() ?: "")
                nodeObject.put("system_uptime", node.systemUptime?.toString() ?: "")
                nodeObject.put("system_load", node.systemLoad?.toString() ?: "")
                nodeObject.put("firmware_version", node.firmwareVersion ?: "")
                nodeObject.put("vpn_connected", node.batmanAdv?.vpnConnected ?: false)
                nodeObject.put("gateway_tq", node.batmanAdv?.tq ?: 0)
                nodeObject.put("neighbors", node.batmanAdv?.neighbors ?: 0)
                nodeObject.put("originators", node.batmanAdv?.originators ?: 0)
                nodeObject.put("clients", node.batmanAdv?.clients ?: 0)
                nodeObject.put("community_short_name", node.communityInformation?.shortName ?: "")
                nodesArray.put(nodeObject)
            }

            jsonObject.put("nodes", nodesArray)
            jsonObject.toString(2)
        } catch (e: Exception) {
            JSONObject().put("error", "Error generating debug info: ${e.message}").toString()
        }
    }
}

fun toggleScanState(wifiScanService: WifiScanService, scanResultsList: ScanResultListModel) {
    if (wifiScanService.scanningEnabled.value) {
        wifiScanService.stopScanning()
        return
    }

    wifiScanService.startScanning()
}

fun getSiteDomainString(node: GluonNode): String? {
    if (node.siteCode == null)
        return null

    var sb = StringBuilder()

    if (node.siteCode != null) {
        if (node.siteCode!!.resolved != null) {
            sb.append(node.siteCode!!.resolved)
        } else {
            sb.append(node.siteCode!!.unresolved)
        }
    }

    if (node.domain != null) {
        sb.append(" (")
        if (node.domain!!.resolved != null) {
            sb.append(node.domain!!.resolved)
        } else {
            sb.append(node.domain!!.unresolved)
        }
        sb.append(")")
    }

    return sb.toString()
}

@Composable
fun getColorForNodeStatus(node: GluonNode): Color {
    return when (NodeStatusService.getNodeStatus(node)) {
        NodeStatus.OK -> LocalCustomColorsPalette.current.successColor
        NodeStatus.MESH_ONLY -> LocalCustomColorsPalette.current.warningColor
        else -> MaterialTheme.colorScheme.errorContainer
    }
}

@Composable
fun getIconForNodeStatus(node: GluonNode): ImageVector {
    return when (NodeStatusService.getNodeStatus(node)) {
        NodeStatus.OK -> Icons.Default.Done
        NodeStatus.MESH_ONLY -> Icons.Default.Warning
        else -> Icons.Default.Close
    }
}

@Composable
fun activityDesign(
    activity: MainActivity,
    wifiScanService: WifiScanService,
    scanResultsList: ScanResultListModel = ScanResultListModel()
) {
    NodeWhispererTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text("Knoten")
                        },
                        actions = {
                            IconButton(onClick = {
                                /* share debug info via share function */
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, activity.generateRawDebugInfo())
                                    type = "text/plain"
                                }
                                val launchBrowser = Intent.createChooser(sendIntent, null)
                                activity.startActivity(launchBrowser)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share"
                                )
                            }
                            IconButton(onClick = {
                                /* Open darmstadt.freifunk.net */
                                val uriUrl = Uri.parse(activity.getString(R.string.url_help))
                                val launchBrowser = Intent(Intent.ACTION_VIEW, uriUrl)
                                activity.startActivity(launchBrowser)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Info"
                                )
                            }
                        },

                        )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (activity.haveAllPermissions()) {
                                toggleScanState(wifiScanService, scanResultsList)
                            } else if (!wifiScanService.scanningEnabled.value) {
                                activity.permissionToast()
                            }
                        },
                        containerColor =
                        if (wifiScanService.scanningEnabled.value)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.wifi_find),
                            contentDescription = "Add",
                            Modifier.background(Color.Transparent)
                        )
                        Text(
                            text =
                            if (wifiScanService.scanningEnabled.value)
                                stringResource(R.string.fab_scan_stop)
                            else
                                stringResource(R.string.fab_scan_start),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    ScanStatusBar(wifiScanService)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(scanResultsList.scanResults) { index, item ->
                            ScanResultListElement(
                                node = item
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NodeInfoBottomSheet(
    node: GluonNode,
    showBottomSheet: MutableState<Boolean>,
    scope: CoroutineScope,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = {
            showBottomSheet.value = false
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = getIconForNodeStatus(node),
                tint = getColorForNodeStatus(node),
                contentDescription = "Status",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(48.dp)
            )
            Text(
                text =
                    if (NodeStatusService.getNodeStatus(node) == NodeStatus.OK)
                        stringResource(R.string.node_status_okay)
                    else if (NodeStatusService.getNodeStatus(node) == NodeStatus.MESH_ONLY)
                        stringResource(R.string.node_status_mesh_only)
                    else
                        stringResource(R.string.node_status_error),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            if (NodeStatusService.getNodeStatus(node) == NodeStatus.MESH_ONLY) {
                Text(
                    text = stringResource(R.string.node_status_mesh_only_description),
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            Text(
                text = stringResource(R.string.node_title_information),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(4.dp)
            )
            NodeInfoDialogProperty(stringResource(R.string.node_name),
                node.hostname ?: stringResource(R.string.str_unknown)
            )
            NodeInfoDialogProperty(stringResource(R.string.node_id),
                node.nodeId.toString()
            )
            if (node.lastSeen != null)
                NodeInfoDialogProperty(stringResource(R.string.node_last_seen),
                    node.lastSeen.toString()
                )
            if (node.systemUptime != null)
                NodeInfoDialogProperty(stringResource(R.string.node_runtime),
                    node.systemUptime.toString()
                )
            if (getSiteDomainString(node) != null)
                NodeInfoDialogProperty(
                    stringResource(R.string.node_community),
                    getSiteDomainString(node)!!
                )
            if (node.systemLoad != null)
                NodeInfoDialogProperty(stringResource(R.string.node_system_load),
                    node.systemLoad.toString()
                )
            if (node.firmwareVersion != null)
                NodeInfoDialogProperty(stringResource(R.string.node_firmware_version),
                    node.firmwareVersion ?: stringResource(R.string.str_unknown)
                )
            if (node.batmanAdv != null) {
                NodeInfoDialogProperty(stringResource(R.string.node_vpn),
                    if (node.batmanAdv!!.vpnConnected) stringResource(R.string.node_vpn_connected) else stringResource(
                        R.string.node_vpn_not_connected
                    )
                )
                NodeInfoDialogProperty(stringResource(R.string.node_gateway),
                    if (node.batmanAdv!!.tq > 0) stringResource(
                        R.string.node_batman_gw_reachable,
                        (node.batmanAdv!!.tq / 255.0 * 100.0).toInt()
                    ) else stringResource(R.string.node_batman_gw_not_reachable)
                )
                NodeInfoDialogProperty(stringResource(R.string.node_neighbors),
                    node.batmanAdv!!.neighbors.toString()
                )
                NodeInfoDialogProperty(stringResource(R.string.node_nodes_in_network),
                    node.batmanAdv!!.originators.toString()
                )
                NodeInfoDialogProperty(stringResource(R.string.node_clients),
                    node.batmanAdv!!.clients.toString()
                )
            }
        }
    }
}

@Composable
fun NodeInfoDialogProperty(name: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = name,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = content,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun ScanStatusBar(wifiScanService: WifiScanService) {
    val isEnabled = wifiScanService.scanningEnabled.value
    val lastTimestamp = wifiScanService.lastScanTimestamp.value
    val barColor = MaterialTheme.colorScheme.secondary
    val isThrottled = wifiScanService.scanThrottleEnabled.value

    if (!isEnabled) {
        return
    }

    if (lastTimestamp == null) {
        ScanStatusRow(
            text = stringResource(R.string.scan_status_waiting),
            color = barColor,
            isThrottled = isThrottled
        )
        return
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    val elapsedSec = ((now - lastTimestamp) / 1000).toInt()
    val elapsedText = if (elapsedSec < 60) {
        stringResource(R.string.scan_status_seconds, elapsedSec)
    } else {
        stringResource(R.string.scan_status_minutes, elapsedSec / 60)
    }

    val progress: Float? = if (isThrottled) (elapsedSec / WifiScanService.THROTTLED_SCAN_INTERVAL_SEC.toFloat()).coerceIn(0f, 1f) else null

    ScanStatusRow(
        text = stringResource(R.string.scan_status_last_scan, elapsedText),
        color = barColor,
        isThrottled = isThrottled,
        progress = progress
    )
}

@Composable
fun ScanStatusRow(text: String, color: Color, isThrottled: Boolean, progress: Float? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                label = "scanStaleness"
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.3f),
                drawStopIndicator = {}
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.3f)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isThrottled) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val iconSize = with(LocalDensity.current) {
                        MaterialTheme.typography.labelSmall.fontSize.toDp()
                    }
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.wifi_scan_throttled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                modifier = Modifier.align(Alignment.CenterEnd),
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ScanResultListElement(node: GluonNode) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clickable {
                showBottomSheet.value = true
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
    {
        Row (
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = node.hostname ?: stringResource(R.string.global_unknown),
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = node.nodeId.toString(),
                    textAlign = TextAlign.Start,
                )
            }
            Icon(
                imageVector = getIconForNodeStatus(node),
                tint = getColorForNodeStatus(node),
                contentDescription = "Add",
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(8.dp)
            )
        }
    }
    if (showBottomSheet.value) {
        NodeInfoBottomSheet(node, showBottomSheet, scope, sheetState)
    }
}