package dev.herod.iot

import io.ktor.client.HttpClient

abstract class SsdpDevice @JvmOverloads constructor(
        override val name: String? = null,
        override val friendlyName: String? = null,
        override val serialNumber: String? = null,
        override var switchState: SwitchState = SwitchState.UNSURE,
        open val httpClient: HttpClient
) : SwitchableDevice
