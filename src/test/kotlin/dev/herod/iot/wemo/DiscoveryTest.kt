package dev.herod.iot.wemo

import dev.herod.iot.DeviceDiscovery
import dev.herod.iot.MyHttpClient.client
import dev.herod.iot.SwitchState
import dev.herod.iot.SwitchableDevice
import dev.herod.iot.get
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class DiscoveryTest {

    @Test
    fun testDiscovery() {

        val job = GlobalScope.launch {
            while (true) {
                println("=== discovery start ===")
                println("=== discovery took ${measureTimeMillis { DeviceDiscovery.start(client).join() }}ms")
            }
        }
        val devices = DeviceDiscovery.devices

        runBlocking {
            while (devices.isEmpty()) delay(800)
            job.cancel()
//            devices.forEach {
//                it.updateState(false)
//            }
//            devices["Bedroom Cabinet"]?.updateState(true)
//            devices["Bedroom Desk"]?.updateState(true)
//            while (true) {
//                devices["Bedroom Bright Light"]?.updateState(true)
//                delay(500)
//                devices["Bedroom Bright Light"]?.updateState(false)
//                delay(500)
//            }
            val devices = devices.filterIsInstance(SwitchableDevice::class.java)
            devices.forEach {
                it.updateState(SwitchState.OFF)
            }
            devices["Bedroom Desk"]?.updateState(SwitchState.ON)
            synchronized(devices) {
                devices.forEach {
                    println(" - ${it.friendlyName} is ${it.switchState}")
                }
                assertTrue(devices.isNotEmpty())
            }
        }
    }
}
