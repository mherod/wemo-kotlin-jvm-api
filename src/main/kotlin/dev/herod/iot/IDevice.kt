package dev.herod.iot

interface IDevice {
    val name: String?
    val friendlyName: String?
    val serialNumber: String?
    val stateUpdateTimeMs: Long

    suspend fun syncState()
    suspend fun updateState(value: Boolean): Boolean
}
