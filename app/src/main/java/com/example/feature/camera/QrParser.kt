package com.example.feature.camera

import android.net.Uri

sealed interface QrPayload {
    val raw: String

    data class Url(override val raw: String, val host: String) : QrPayload
    data class Wifi(override val raw: String, val ssid: String, val security: String, val password: String) : QrPayload
    data class Phone(override val raw: String, val number: String) : QrPayload
    data class Email(override val raw: String, val address: String, val subject: String?) : QrPayload
    data class Contact(
        override val raw: String,
        val name: String,
        val phone: String?,
        val email: String?,
        val org: String?,
    ) : QrPayload
    data class PlainText(override val raw: String) : QrPayload
}

object QrParser {
    fun parse(rawInput: String): QrPayload {
        val raw = rawInput.trim()
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            val host = runCatching { Uri.parse(raw).host }.getOrNull().orEmpty().ifBlank { raw }
            return QrPayload.Url(raw, host)
        }
        if (raw.startsWith("WIFI:", ignoreCase = true)) {
            val body = raw.substring(5)
            val fields = parseWifiFields(body)
            val ssid = fields["S"].orEmpty()
            if (ssid.isNotBlank()) {
                return QrPayload.Wifi(
                    raw = raw,
                    ssid = ssid,
                    security = fields["T"].orEmpty().ifBlank { "Open" },
                    password = fields["P"].orEmpty(),
                )
            }
        }
        if (raw.startsWith("tel:", ignoreCase = true)) {
            val num = raw.substring(4).trim()
            if (num.isNotEmpty()) return QrPayload.Phone(raw, num)
        }
        if (raw.startsWith("mailto:", ignoreCase = true)) {
            val withoutScheme = raw.substring(7)
            val qIdx = withoutScheme.indexOf('?')
            val addr = (if (qIdx >= 0) withoutScheme.substring(0, qIdx) else withoutScheme).trim()
            val subject = if (qIdx >= 0) {
                withoutScheme.substring(qIdx + 1)
                    .split('&')
                    .firstOrNull { it.substringBefore('=').equals("subject", ignoreCase = true) }
                    ?.substringAfter('=', "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
            } else null
            if (addr.isNotEmpty()) return QrPayload.Email(raw, addr, subject)
        }
        if (raw.contains("BEGIN:VCARD", ignoreCase = true)) {
            val lines = raw.lines()
            fun field(prefix: String): String? =
                lines.firstOrNull { it.uppercase().startsWith(prefix) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            val name = field("FN") ?: field("N")?.replace(';', ' ')?.trim()
            val phone = field("TEL")
            val email = field("EMAIL")
            val org = field("ORG")
            if (!name.isNullOrBlank() || !phone.isNullOrBlank() || !email.isNullOrBlank()) {
                return QrPayload.Contact(raw, name ?: "Contact", phone, email, org)
            }
        }
        return QrPayload.PlainText(raw)
    }

    private fun parseWifiFields(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val sb = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch == '\\' && i + 1 < body.length) {
                sb.append(body[i + 1])
                i += 2
                continue
            }
            if (ch == ':' && key == null) {
                key = sb.toString().uppercase()
                sb.clear()
            } else if (ch == ';') {
                if (key != null) {
                    out[key] = sb.toString()
                }
                key = null
                sb.clear()
            } else {
                sb.append(ch)
            }
            i++
        }
        if (key != null && sb.isNotEmpty()) {
            out[key] = sb.toString()
        }
        return out
    }
}
