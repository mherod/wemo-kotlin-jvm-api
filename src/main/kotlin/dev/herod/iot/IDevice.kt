package dev.herod.iot

import io.ktor.client.HttpClient

interface IDevice {
    val name: String?
    val friendlyName: String?
    val serialNumber: String?
    val stateUpdateTimeMs: Long
    val switchState: Boolean
    val httpClient: HttpClient?

    suspend fun syncState()
    suspend fun updateState(value: Boolean): Boolean
}

operator fun <T : IDevice> List<T>.get(s: String): T? {
    return firstOrNull { it.friendlyName == s }
}
