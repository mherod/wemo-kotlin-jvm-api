package dev.herod.iot.wemo

import dev.herod.iot.IDevice

abstract class Device @JvmOverloads constructor(
        override val name: String? = null,
        override var friendlyName: String? = null,
        override var serialNumber: String? = null,
        internal var internalState: Boolean = false
) : IDevice
