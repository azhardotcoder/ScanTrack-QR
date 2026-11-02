package com.scantrack.qr.data.parser

/**
 * Standard representation of parsed Wi-Fi network credentials.
 * Follows the ZXing / Android Wi-Fi QR code specification:
 * WIFI:T:<security>;S:<ssid>;P:<password>;H:<hidden>;;
 */
data class WiFiNetworkData(
    val ssid: String,
    val password: String = "",
    val security: String = "nopass", // WPA, WEP, nopass, WPA3, etc.
    val isHidden: Boolean = false,
    val isValid: Boolean = true,
    val validationError: String? = null
)

object WiFiQrParser {

    /**
     * Parses a raw QR string adhering to standard Wi-Fi format.
     * Accurately parses escaped characters (\;, \:, \\, \,).
     */
    fun parse(raw: String): WiFiNetworkData {
        if (!raw.startsWith("WIFI:", ignoreCase = true)) {
            return WiFiNetworkData(
                ssid = "",
                isValid = false,
                validationError = "Not a Wi-Fi QR code: must start with WIFI:"
            )
        }

        val content = raw.substring(5) // Remove "WIFI:"
        val fields = mutableMapOf<String, String>()

        var i = 0
        val len = content.length

        while (i < len) {
            // Find key (letters before ':')
            val colonIndex = content.indexOf(':', i)
            if (colonIndex == -1) break

            val key = content.substring(i, colonIndex).trim()
            i = colonIndex + 1

            // Parse value until unescaped ';'
            val sb = java.lang.StringBuilder()
            var isEscaped = false

            while (i < len) {
                val c = content[i]
                if (isEscaped) {
                    sb.append(c)
                    isEscaped = false
                } else if (c == '\\') {
                    isEscaped = true
                } else if (c == ';') {
                    i++ // consume the ';'
                    break
                } else {
                    sb.append(c)
                }
                i++
            }
            fields[key] = sb.toString()
        }

        val ssid = fields["S"] ?: ""
        val password = fields["P"] ?: ""
        val securityRaw = (fields["T"] ?: "nopass").trim().uppercase()
        val hiddenStr = fields["H"]?.trim()?.lowercase()
        val isHidden = hiddenStr == "true" || hiddenStr == "1"

        if (ssid.isEmpty()) {
            return WiFiNetworkData(
                ssid = "",
                isValid = false,
                validationError = "Missing SSID (S:) parameter"
            )
        }

        val normalizedSecurity = when (securityRaw) {
            "WPA", "WPA2", "WPA/WPA2" -> "WPA"
            "WPA3", "SAE" -> "WPA3"
            "WEP" -> "WEP"
            "NOPASS", "", "NONE", "OPEN" -> "nopass"
            else -> securityRaw
        }

        // Passwords for WPA/WPA2 must be at least 8 characters if not open
        if ((normalizedSecurity == "WPA" || normalizedSecurity == "WPA3") && password.isNotEmpty() && password.length < 8) {
            return WiFiNetworkData(
                ssid = ssid,
                password = password,
                security = normalizedSecurity,
                isHidden = isHidden,
                isValid = false,
                validationError = "WPA passphrase must be at least 8 characters"
            )
        }

        return WiFiNetworkData(
            ssid = ssid,
            password = password,
            security = normalizedSecurity,
            isHidden = isHidden,
            isValid = true
        )
    }
}

