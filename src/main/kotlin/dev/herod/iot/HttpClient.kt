package dev.herod.iot

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging

object HttpClient {
    @JvmStatic
    val client = HttpClient(Apache) {
        install(Logging) {
            level = LogLevel.ALL
        }
    }
}
