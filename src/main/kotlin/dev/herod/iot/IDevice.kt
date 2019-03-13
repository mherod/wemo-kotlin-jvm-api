package dev.herod.iot

interface IDevice {
    val name: String?
    var friendlyName: String?
    var serialNumber: String?
    val stateUpdateTimeMs: Long

    suspend fun syncState()
    suspend fun updateState(value: Boolean): Boolean
}
