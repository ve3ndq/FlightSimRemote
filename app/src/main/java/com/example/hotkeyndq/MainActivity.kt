package com.example.hotkeyndq

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        id = "flight",
        title = "Flight",
        commands = listOf(
            HotKeyCommand("GEAR_TOGGLE", "Gear"),
            HotKeyCommand("FLAPS_UP", "Flaps Up"),
            HotKeyCommand("FLAPS_DOWN", "Flaps Down"),
            HotKeyCommand("PARK_BRAKE_TOGGLE", "Park Brk"),
            HotKeyCommand("AP_MASTER_TOGGLE", "AP"),
            HotKeyCommand("AP_HDG_HOLD", "HDG Hold"),
        )
    ),
    HotKeyPage(
        id = "view",
        title = "View",
        commands = listOf(
            HotKeyCommand("VIEW_RESET", "View Reset"),
            HotKeyCommand("VIEW_COCKPIT", "Cockpit"),
            HotKeyCommand("VIEW_EXTERNAL", "External"),
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