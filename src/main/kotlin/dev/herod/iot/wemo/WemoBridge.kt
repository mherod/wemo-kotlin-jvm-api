package dev.herod.iot.wemo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.net.*
import java.util.*
import kotlin.system.measureTimeMillis

class WemoBridge {

    fun discovery() {

        val destAddress = InetSocketAddress(InetAddress.getByName(SSDP_IP), SSDP_PORT)

        val discoveryMessageBytes = StringBuffer().apply {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_IP:$SSDP_PORT\r\n")
            append("ST: upnp:rootdevice\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("\r\n")
        }.toString().toByteArray()

        val discoveryPacket = DatagramPacket(
                discoveryMessageBytes,
                discoveryMessageBytes.size,
                destAddress
        )

        val wildSocket = DatagramSocket(SSDP_SEARCH_PORT)
        try {
            wildSocket.soTimeout = TIMEOUT
            wildSocket.send(discoveryPacket)

            while (true) {
                try {
                    val size = 2048
                    val receivePacket = DatagramPacket(ByteArray(size), size)
                    wildSocket.receive(receivePacket)
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
            wildSocket.disconnect()
            wildSocket.close()
        }
    }

    private fun messageToHeaders(message: String): Map<String, String> {
        val map = HashMap<String, String>()

        message.split("\n".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
                .forEach { pair ->
                    try {
                        val p = pair.split(":".toRegex(), 2).toTypedArray()
                        map[p[0].trim { it <= ' ' }] = p[1].trim { it <= ' ' }
                    } catch (ignored: Exception) {
                    }
                }
        return map
    }

    private suspend fun addDevice(message: String) {
        try {
            val map = messageToHeaders(message)

            val device = WemoSwitch().apply {
                name = message
                headers.putAll(map)
            }

            val location = map["LOCATION"]
            val url = try {
                URL(location)
            } catch (e: Exception) {
                return
            }

            device.location = location?.replace("(http://.*)/.*".toRegex(), "$1")

            val inputStream = url.openStream().buffered()
            val xml = XmlMapper().readTree(inputStream)

            val objectMapper = ObjectMapper()
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true)

//            if ("belkin" !in objectMapper.writeValueAsString(xml)) {
//                return
//            }

            val friendlyName = xml.get("device").get("friendlyName").asText()
            device.friendlyName = friendlyName
            device.name = friendlyName.toLowerCase().replace(" ", "_")
            device.serialNumber = try {
                xml.get("device").get("serialNumber").asText()
            } catch (throwable: Throwable) {
                null
            }

            withContext(IO) {
                device.syncState()
            }
            addDevice(device)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

        @Throws(Exception::class)
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

private operator fun List<Device>.get(s: String): Device? {
    return firstOrNull { it.friendlyName == s }
}
