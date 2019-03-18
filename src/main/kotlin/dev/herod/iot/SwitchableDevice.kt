package dev.herod.iot

interface SwitchableDevice : IDevice {
    val switchState: SwitchState
    suspend fun updateState(value: SwitchState): Boolean
}
