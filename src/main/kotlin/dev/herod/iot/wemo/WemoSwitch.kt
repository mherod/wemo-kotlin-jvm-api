package dev.herod.iot.wemo

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
) : Device(httpClient = httpClient) {

    override suspend fun updateState(value: Boolean): Boolean {
        call(
                endpoint = endpoint,
                soapCall = "urn:Belkin:service:basicevent:1#SetBinaryState",
                content = setBinaryStateContent.replace("{{state}}", if (value) "1" else "0")
        ).also { response ->
            if ("error" !in response.toLowerCase())
                switchState = value
        }
        return true
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

        switchState = "1" in groupValues
        stateUpdateTimeMs = System.currentTimeMillis()
    }

    companion object {

        @Language("XML")
        const val setBinaryStateContent = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:SetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
            <BinaryState>1</BinaryState>
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
