package dev.herod.iot.wemo

import dev.herod.iot.DeviceDiscovery
import dev.herod.iot.MyHttpClient.client
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
            while (devices.size == 0) delay(800)
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
            synchronized(devices) {
                devices.forEach {
                    println(" - ${it.friendlyName} is ${it.switchState}")
                }
                assertTrue(devices.isNotEmpty())
            }
        }
    }
}
