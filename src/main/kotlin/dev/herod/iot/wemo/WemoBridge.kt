package dev.herod.iot.wemo

import dev.herod.iot.DeviceDiscovery.devices
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.*

object WemoBridge {

    @JvmStatic
    fun discovery(httpClient: HttpClient): Job = GlobalScope.launch {

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

        lateinit var socket: DatagramSocket
        try {
            socket = DatagramSocket(SSDP_SEARCH_PORT)
            socket.soTimeout = TIMEOUT
            withContext(IO) {
                socket.send(discoveryPacket)
            }
            while (true) {
                try {
                    val size = 2048
                    val receivePacket = DatagramPacket(ByteArray(size), size)
                    withContext(IO) {
                        socket.receive(receivePacket)
                    }
                    val message = receivePacket.data.toString(Charsets.UTF_8)

                    launch {
                        addDevice(message, httpClient)
                    }
                } catch (e: SocketTimeoutException) {
                    println(e)
                    break
                }
            }
        } finally {
            socket.disconnect()
            socket.close()
        }
    }

    private suspend fun addDevice(message: String, httpClient: HttpClient) {
        try {

            val headers = message.split("\n")
                    .map { line ->
                        line.split(":".toRegex(), 2)
                                .map { it.trim() }
                                .filterNot { it.isEmpty() }
                                .let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
                    }.toMap().toMutableMap()

            val setupUrl = headers["LOCATION"]
            val url = try {
                URL(setupUrl)
            } catch (e: Throwable) {
                return
            }
            val xmlString = try {
                httpClient.get<String>(url = url)
            } catch (e: Throwable) {
                return
            }
            val deviceType = xmlString.getXmlNodeContents("deviceType") ?: return
            val friendlyName = xmlString.getXmlNodeContents("friendlyName") ?: return
            val name = friendlyName.toLowerCase().replace(" ", "_")
            val serialNumber = xmlString.getXmlNodeContents("serialNumber") ?: return
            val location = setupUrl?.firstGroup("(http://.*)/.*".toRegex()) ?: return

            if (deviceType != "urn:Belkin:device:controllee:1") return

            val device = WemoSwitch(
                    name = name,
                    friendlyName = friendlyName,
                    location = location,
                    headers = headers,
                    httpClient = httpClient,
                    serialNumber = serialNumber
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
            "<$nodeName>(.*)</$nodeName>".toRegex().find(this)?.groupValues?.lastOrNull()

    private fun addDevice(device: SsdpDevice) {
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

    private const val SSDP_PORT = 1900
    private const val SSDP_SEARCH_PORT = 1903
    private const val SSDP_IP = "239.255.255.250"
    private const val TIMEOUT = 6000
}

private fun String.firstGroup(toRegex: Regex): String {
    return replace(toRegex, "$1")
}

