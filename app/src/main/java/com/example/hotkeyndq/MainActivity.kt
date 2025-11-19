package com.example.hotkeyndq

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

// Simple command model
data class HotKeyCommand(
    val id: String,
    val label: String
)

// Fixed page model (can grow later)
data class HotKeyPage(
    val id: String,
    val title: String,
    val commands: List<HotKeyCommand>
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("hotkey_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", "192.168.0.100") ?: "192.168.0.100"
        val savedPort = prefs.getInt("server_port", 5555)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        initialIp = savedIp,
                        initialPort = savedPort,
                        onSaveConnection = { ip, port ->
                            prefs.edit()
                                .putString("server_ip", ip)
                                .putInt("server_port", port)
                                .apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    initialIp: String,
    initialPort: Int,
    onSaveConnection: (String, Int) -> Unit
) {
    var serverIp by remember { mutableStateOf(initialIp) }
    var serverPortText by remember { mutableStateOf(initialPort.toString()) }
    var statusText by remember { mutableStateOf("Not connected (will open socket per command)") }

    val pages = remember { defaultPages() }
    var currentPageIndex by remember { mutableStateOf(0) }

    val contentScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(contentScrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connection controls
        Text(text = "Connection", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(2f),
                value = serverIp,
                onValueChange = { serverIp = it },
                label = { Text("Server IP") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = serverPortText,
                onValueChange = { serverPortText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Port") },
                singleLine = true
            )
        }

        Button(onClick = {
            val port = serverPortText.toIntOrNull() ?: 0
            if (port in 1..65535) {
                onSaveConnection(serverIp, port)
                statusText = "Saved: $serverIp:$port"
            } else {
                statusText = "Invalid port"
            }
        }) {
            Text("Save")
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Simple page selector (fixed pages)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            pages.forEachIndexed { index, page ->
                OutlinedButton(
                    onClick = { currentPageIndex = index },
                    enabled = currentPageIndex != index
                ) {
                    Text(page.title)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buttons for current page
        Text(
            text = pages[currentPageIndex].title,
            style = MaterialTheme.typography.titleMedium
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            pages[currentPageIndex].commands.chunked(3).forEach { rowCommands ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowCommands.forEach { command ->
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            onClick = {
                                val port = serverPortText.toIntOrNull()
                                if (port == null || port !in 1..65535) {
                                    statusText = "Invalid port"
                                } else {
                                    sendCommand(
                                        ip = serverIp,
                                        port = port,
                                        commandId = command.id
                                    ) { ok, msg ->
                                        statusText = msg
                                    }
                                }
                            }
                        ) {
                            Text(command.label)
                        }
                    }
                    if (rowCommands.size < 3) {
                        repeat(3 - rowCommands.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// A very simple fixed-page definition to start with
private fun defaultPages(): List<HotKeyPage> = listOf(
    HotKeyPage(
        id = "power",
        title = "Power",
        commands = listOf(
            HotKeyCommand("TOGGLE_MASTER_BATTERY", "Battery"),
            HotKeyCommand("TOGGLE_MASTER_ALTERNATOR", "Alternator"),
            HotKeyCommand("AVIONICS_MASTER_ON", "Avionics On"),
            HotKeyCommand("AVIONICS_MASTER_OFF", "Avionics Off"),
            HotKeyCommand("FUEL_PUMP", "Fuel Pump"),
            HotKeyCommand("PITOT_HEAT_TOGGLE", "Pitot Heat"),
            HotKeyCommand("CARB_HEAT_ON", "Carb Heat On"),
            HotKeyCommand("CARB_HEAT_OFF", "Carb Heat Off"),
            HotKeyCommand("DE_ICE_TOGGLE", "De-Ice"),
            HotKeyCommand("ENGINE_PRIMER", "Primer"),
        )
    ),
    HotKeyPage(
        id = "engine",
        title = "Engine",
        commands = listOf(
            HotKeyCommand("ENGINE_AUTOSTART", "Auto Start"),
            HotKeyCommand("ENGINE_AUTO_SHUTDOWN", "Auto Stop"),
            HotKeyCommand("MAGNETO_OFF", "Mag Off"),
            HotKeyCommand("MAGNETO_LEFT", "Mag Left"),
            HotKeyCommand("MAGNETO_RIGHT", "Mag Right"),
            HotKeyCommand("MAGNETO_BOTH", "Mag Both"),
            HotKeyCommand("MAGNETO_START", "Start"),
            HotKeyCommand("THROTTLE_INCR", "Throttle +"),
            HotKeyCommand("THROTTLE_DECR", "Throttle -"),
            HotKeyCommand("MIXTURE_INCR", "Mixture +"),
            HotKeyCommand("MIXTURE_DECR", "Mixture -"),
            HotKeyCommand("PROP_PITCH_INCR", "Prop +"),
            HotKeyCommand("PROP_PITCH_DECR", "Prop -"),
            HotKeyCommand("THROTTLE_FULL", "Throttle Full"),
            HotKeyCommand("THROTTLE_CUT", "Throttle Cut"),
        )
    ),
    HotKeyPage(
        id = "flight_controls",
        title = "Flight Controls",
        commands = listOf(
            HotKeyCommand("GEAR_TOGGLE", "Gear"),
            HotKeyCommand("FLAPS_UP", "Flaps Up"),
            HotKeyCommand("FLAPS_DOWN", "Flaps Down"),
            HotKeyCommand("FLAPS_INCR", "Flaps +"),
            HotKeyCommand("FLAPS_DECR", "Flaps -"),
            HotKeyCommand("ELEV_TRIM_UP", "Trim Up"),
            HotKeyCommand("ELEV_TRIM_DN", "Trim Down"),
            HotKeyCommand("AILERON_TRIM_LEFT", "Aileron L"),
            HotKeyCommand("AILERON_TRIM_RIGHT", "Aileron R"),
            HotKeyCommand("RUDDER_TRIM_LEFT", "Rudder L"),
            HotKeyCommand("RUDDER_TRIM_RIGHT", "Rudder R"),
            HotKeyCommand("PARKING_BRAKES", "Park Brake"),
            HotKeyCommand("BRAKES_LEFT", "Brake Left"),
            HotKeyCommand("BRAKES_RIGHT", "Brake Right"),
            HotKeyCommand("YAW_DAMPER_TOGGLE", "Yaw Damper"),
        )
    ),
    HotKeyPage(
        id = "autopilot",
        title = "Autopilot / Nav",
        commands = listOf(
            HotKeyCommand("AP_MASTER", "AP Master"),
            HotKeyCommand("AP_HDG_HOLD", "HDG Hold"),
            HotKeyCommand("AP_NAV1_HOLD", "NAV Hold"),
            HotKeyCommand("AP_ALT_HOLD", "ALT Hold"),
            HotKeyCommand("AP_APR_HOLD", "APR"),
            HotKeyCommand("AP_BC_HOLD", "BC"),
            HotKeyCommand("AP_PANEL_VS_ON", "VS Hold"),
            HotKeyCommand("AP_PANEL_VS_OFF", "VS Off"),
            HotKeyCommand("AP_VS_VAR_INC", "VS +"),
            HotKeyCommand("AP_VS_VAR_DEC", "VS -"),
            HotKeyCommand("AP_PANEL_SPEED_ON", "IAS Hold"),
            HotKeyCommand("AP_PANEL_SPEED_OFF", "IAS Off"),
            HotKeyCommand("AP_SPD_VAR_INC", "IAS +"),
            HotKeyCommand("AP_SPD_VAR_DEC", "IAS -"),
            HotKeyCommand("AP_ALT_VAR_INC", "ALT +"),
            HotKeyCommand("AP_ALT_VAR_DEC", "ALT -"),
            HotKeyCommand("HEADING_BUG_INC", "HDG Bug +"),
            HotKeyCommand("HEADING_BUG_DEC", "HDG Bug -"),
        )
    ),
    HotKeyPage(
        id = "lights",
        title = "Lights",
        commands = listOf(
            HotKeyCommand("LANDING_LIGHTS_TOGGLE", "Landing"),
            HotKeyCommand("TAXI_LIGHTS_TOGGLE", "Taxi"),
            HotKeyCommand("NAV_LIGHTS_TOGGLE", "Nav"),
            HotKeyCommand("BEACON_LIGHTS_TOGGLE", "Beacon"),
            HotKeyCommand("STROBES_TOGGLE", "Strobes"),
            HotKeyCommand("PANEL_LIGHTS_TOGGLE", "Panel"),
            HotKeyCommand("CABIN_LIGHTS_TOGGLE", "Cabin"),
            HotKeyCommand("LOGO_LIGHTS_TOGGLE", "Logo"),
            HotKeyCommand("INSTRUMENT_LIGHTS_TOGGLE", "Instrument"),
            HotKeyCommand("RECOGNITION_LIGHTS_TOGGLE", "Recognition"),
        )
    ),
    HotKeyPage(
        id = "camera",
        title = "Cameras",
        commands = listOf(
            HotKeyCommand("VIEW_RESET", "Reset"),
            HotKeyCommand("VIEW_VIRTUAL_COCKPIT", "Cockpit"),
            HotKeyCommand("VIEW_SPOT", "External"),
            HotKeyCommand("VIEW_TOP_DOWN", "Top Down"),
            HotKeyCommand("VIEW_FORWARD", "Forward"),
            HotKeyCommand("VIEW_FORWARD_LEFT", "Fwd Left"),
            HotKeyCommand("VIEW_LEFT", "Left"),
            HotKeyCommand("VIEW_FORWARD_RIGHT", "Fwd Right"),
            HotKeyCommand("VIEW_RIGHT", "Right"),
            HotKeyCommand("VIEW_REAR", "Rear"),
            HotKeyCommand("VIEW_UP", "Up"),
            HotKeyCommand("VIEW_DOWN", "Down"),
            HotKeyCommand("VIEW_NEXT", "Next"),
            HotKeyCommand("VIEW_PREVIOUS", "Previous"),
            HotKeyCommand("CAMERA_DRONE_TOGGLE", "Drone"),
            HotKeyCommand("CAMERA_DRONE_RESET", "Drone Reset"),
            HotKeyCommand("CAMERA_DRONE_ATTACH", "Drone Attach"),
            HotKeyCommand("CAMERA_DRONE_DETACH", "Drone Detach"),
            HotKeyCommand("CAMERA_DRONE_LOCK_TOGGLE", "Drone Lock"),
        )
    )
)

// Minimal JSON-over-TCP sender. Opens a new socket per command for now.
private fun sendCommand(
    ip: String,
    port: Int,
    commandId: String,
    onResult: (Boolean, String) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 1000)
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            val json = """{"type":"command","id":"$commandId"}"""
            writer.write(json)
            writer.write("\n")
            writer.flush()
            socket.close()
            onResult(true, "Sent: $commandId")
        } catch (e: Exception) {
            onResult(false, "Error: ${e.message}")
        }
    }
}