package dev.herod.iot

sealed class SwitchState {
    object ON : SwitchState()
    object OFF : SwitchState()
    object UNSURE : SwitchState()
}
