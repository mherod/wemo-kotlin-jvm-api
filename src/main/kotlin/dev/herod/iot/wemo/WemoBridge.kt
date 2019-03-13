package dev.herod.iot.wemo

import dev.herod.iot.HttpClient.client
import io.ktor.client.request.get
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.net.*
import kotlin.system.measureTimeMillis

class WemoBridge {

    fun discovery() {

        val destAddress = InetSocketAddress(InetAddress.getByName(SSDP_IP), SSDP_PORT)

        val discoveryPacket = StringBuffer().apply {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_IP:$SSDP_PORT\r\n")
            append("ST: upnp:rootdevice\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("\r\n")
        }.toString().toByteArray().let { discoveryMessageBytes ->
            DatagramPacket(
                    discoveryMessageBytes,
                    discoveryMessageBytes.size,
                    destAddress
            )
        }

        val socket = DatagramSocket(SSDP_SEARCH_PORT)
        try {
            socket.soTimeout = TIMEOUT
            socket.send(discoveryPacket)

            while (true) {
                try {
                    val size = 2048
                    val receivePacket = DatagramPacket(ByteArray(size), size)
                    socket.receive(receivePacket)
                    val message = receivePacket.data.toString(Charsets.UTF_8)
                    println(message)
                    GlobalScope.launch {
                        addDevice(message)
                    }
                } catch (e: SocketTimeoutException) {
                    break
                }
            }
        } finally {
            socket.disconnect()
            socket.close()
        }
    }

    private suspend fun addDevice(message: String) {
        try {

            val headers = message.split("\n")
                    .map { line ->
                        line.split(":".toRegex(), 2)
                                .map { it.trim() }
                                .filterNot { it.isEmpty() }
                                .let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
                    }.toMap().toMutableMap()

            val setupUrl = headers["LOCATION"]
            val url = URL(setupUrl)
            val xmlString = client.get<String>(url = url)
            val deviceType = xmlString.getXmlNodeContents("deviceType") ?: return
            val friendlyName = xmlString.getXmlNodeContents("friendlyName") ?: return
            val name = friendlyName.toLowerCase().replace(" ", "_")
//            val serialNumber = xmlString.getXmlNodeContents("serialNumber") ?: return
            val location = setupUrl?.firstGroup("(http://.*)/.*".toRegex()) ?: return

            if (deviceType != "urn:Belkin:device:controllee:1") return

            val device = WemoSwitch(
                    name = name,
                    friendlyName = friendlyName,
                    location = location,
                    headers = headers
            )

            withContext(IO) {
                device.syncState()
            }
            addDevice(device)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun String.getXmlNodeContents(nodeName: String) =
            "<$nodeName>(.*)</$nodeName>".toRegex().find(this)?.value

    private fun addDevice(device: Device) {
        val oldestAllowedTimeMs = System.currentTimeMillis() - 5 * 60 * 1000
        synchronized(devices) {
            if (!devices.any { it.serialNumber == device.serialNumber }) {
                println("found $device")
            }
            devices.removeAll { it.serialNumber == device.serialNumber }
            devices.retainAll { it.stateUpdateTimeMs > oldestAllowedTimeMs }
            devices += device
            devices.sortBy { it.name }
        }
    }

    companion object {

        internal val devices = mutableListOf<Device>()

        @JvmStatic
        fun main(args: Array<String>) {

            val bridge = WemoBridge()

            val job = GlobalScope.launch {
                while (true) {
                    println("=== discovery start ===")
                    println("=== discovery took ${measureTimeMillis { bridge.discovery() }}ms")
                }
            }

            runBlocking {
                delay(3000)
//                devices.forEach {
//                    it.updateState(false)
//                }
//                devices["Bedroom Cabinet"]?.updateState(true)
//                devices["Bedroom Desk"]?.updateState(true)
//                while (true) {
//                    devices["Bedroom Bright Light"]?.updateState(true)
//                    delay(500)
//                    devices["Bedroom Bright Light"]?.updateState(false)
//                    delay(500)
//                }
                synchronized(devices) {
                    devices.forEach {
                        println(" - ${it.friendlyName} is ${it.internalState}")
                    }
                }
                job.cancel()
            }
        }

        private const val SSDP_PORT = 1900
        private const val SSDP_SEARCH_PORT = 1903
        private const val SSDP_IP = "239.255.255.250"
        private const val TIMEOUT = 5000
    }
}

private fun String.firstGroup(toRegex: Regex): String {
    return replace(toRegex, "$1")
}

private operator fun List<Device>.get(s: String): Device? {
    return firstOrNull { it.friendlyName == s }
}
