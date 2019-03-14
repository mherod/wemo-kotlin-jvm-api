package dev.herod.iot

import dev.herod.iot.MyHttpClient.client
import dev.herod.iot.wemo.WemoBridge
import io.ktor.client.HttpClient

object DeviceDiscovery {
    val devices = mutableListOf<IDevice>()

    @JvmOverloads
    @JvmStatic
    fun start(httpClient: HttpClient = client) = WemoBridge.discovery(httpClient)
}
