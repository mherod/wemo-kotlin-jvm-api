package dev.herod.iot.wemo

import dev.herod.iot.SsdpDevice
import dev.herod.iot.SwitchState
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import kotlinx.io.charsets.Charset
import org.intellij.lang.annotations.Language

data class WemoSwitch @JvmOverloads constructor(
        override val name: String? = null,
        override val friendlyName: String? = null,
        override val serialNumber: String,
        val location: String? = null,
        val headers: MutableMap<String, String> = mutableMapOf(),
        override var stateUpdateTimeMs: Long = System.currentTimeMillis(),
        override val httpClient: HttpClient
) : SsdpDevice(httpClient = httpClient) {

    override suspend fun updateState(value: SwitchState): Boolean {

        if (value !is SwitchState.ON && value !is SwitchState.OFF) {
            throw IllegalArgumentException("Valid states are either ON or OFF")
        }
        call(
                endpoint = endpoint,
                soapCall = "urn:Belkin:service:basicevent:1#SetBinaryState",
                content = setBinaryStateContent.replace(
                        regex = "<BinaryState>(.*)</BinaryState>".toRegex(),
                        replacement = "<BinaryState>${
                        if (value is SwitchState.ON) "1" else "0"
                }</BinaryState>"
                )
        ).also { response ->
            if ("error" !in response.toLowerCase())
                switchState = value
        }
        return switchState
    }

    override suspend fun syncState() {

        val resp = call(
                endpoint = endpoint,
                soapCall = "urn:Belkin:service:basicevent:1#GetBinaryState",
                content = getBinaryStatePayload
        )

        val groupValues = "<BinaryState>([01])</BinaryState>"
                .toRegex()
                .find(resp)?.groupValues.orEmpty()

        switchState = if ("1" in groupValues) SwitchState.ON else if ("0" in groupValues) SwitchState.OFF else SwitchState.UNSURE
        stateUpdateTimeMs = System.currentTimeMillis()
    }

    companion object {

        @Language("XML")
        const val setBinaryStateContent = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:SetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
            <BinaryState>{{s}}</BinaryState>
        </u:SetBinaryState>
    </s:Body>
</s:Envelope>"""

        @Language("XML")
        const val getBinaryStatePayload = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetBinaryState xmlns:u="urn:Belkin:service:basicevent:1"></u:GetBinaryState>
    </s:Body>
</s:Envelope>"""

        private const val endpoint = "/upnp/control/basicevent1"
    }
}

suspend fun WemoSwitch.call(
        endpoint: String,
        soapCall: String,
        content: String
): String = httpClient.post(
        body = TextContent(
                text = content,
                contentType = ContentType.Text.Xml.withCharset(Charset.forName("utf-8"))
        ),
        block = {
            url("$location$endpoint")
            header("SOAPACTION", "\"$soapCall\"")
        }
)
