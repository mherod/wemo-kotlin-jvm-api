package dev.herod.iot

interface IDevice {
    val name: String?
    val friendlyName: String?
    val serialNumber: String?
    val stateUpdateTimeMs: Long

    suspend fun syncState()
}

operator fun <T : IDevice> List<T>.get(s: String): T? {
    return firstOrNull { it.friendlyName == s }
}
