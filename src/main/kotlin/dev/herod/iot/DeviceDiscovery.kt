package dev.herod.iot

import dev.herod.iot.wemo.WemoBridge

object DeviceDiscovery {
    val devices = mutableListOf<IDevice>()
    fun start() = WemoBridge.discovery()
}
