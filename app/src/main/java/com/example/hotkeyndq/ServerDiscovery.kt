package com.example.hotkeyndq

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class ServerInfo(
    val ip: String,
    val port: Int,
    val name: String
)

class ServerDiscovery {
    companion object {
        private const val TAG = "ServerDiscovery"
        private const val DISCOVERY_PORT = 5556
        private const val DISCOVERY_MESSAGE = "HOTKEYNDQ_DISCOVER"
        private const val TIMEOUT_MS = 3000L
        
        suspend fun discoverServer(): ServerInfo? = withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        socket.soTimeout = TIMEOUT_MS.toInt()
                        
                        // Send broadcast discovery message
                        val sendData = DISCOVERY_MESSAGE.toByteArray()
                        val broadcastAddress = InetAddress.getByName("255.255.255.255")
                        val sendPacket = DatagramPacket(
                            sendData,
                            sendData.size,
                            broadcastAddress,
                            DISCOVERY_PORT
                        )
                        socket.send(sendPacket)
                        Log.d(TAG, "Sent discovery broadcast")
                        
                        // Wait for response
                        val receiveData = ByteArray(1024)
                        val receivePacket = DatagramPacket(receiveData, receiveData.size)
                        socket.receive(receivePacket)
                        
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        Log.d(TAG, "Received response: $response")
                        
                        // Parse JSON response
                        val json = JSONObject(response)
                        ServerInfo(
                            ip = json.getString("ip"),
                            port = json.getInt("port"),
                            name = json.getString("name")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Discovery failed", e)
                    null
                }
            }
        }
    }
}
