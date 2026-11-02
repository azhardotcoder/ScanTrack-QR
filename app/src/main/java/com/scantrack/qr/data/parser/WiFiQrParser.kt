package com.scantrack.qr.data.parser

/**
 * Standard representation of parsed Wi-Fi network credentials.
 * Follows the ZXing / Android Wi-Fi QR code specification:
 * WIFI:T:<security>;S:<ssid>;P:<password>;H:<hidden>;;
 */
data class WiFiNetworkData(
    val ssid: String,
    val password: String = "",
    val security: String = "nopass", // WPA, WPA3, nopass
    val isHidden: Boolean = false,
    val isValid: Boolean = true,
    val validationError: String? = null
)

object WiFiQrParser {

    /**
     * Parses a raw QR string adhering to standard Wi-Fi format.
     * Accurately parses escaped characters (\;, \:, \\, \,).
     * Rejects unsupported security protocols and secured networks with missing passwords.
     */
    fun parse(raw: String): WiFiNetworkData {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("WIFI:", ignoreCase = true)) {
            return WiFiNetworkData(
                ssid = "",
                isValid = false,
                validationError = "Not a Wi-Fi QR code: payload must start with WIFI:"
            )
        }

        val content = trimmed.substring(5) // Remove "WIFI:"
        val fields = mutableMapOf<String, String>()

        var i = 0
        val len = content.length

        while (i < len) {
            val colonIndex = content.indexOf(':', i)
            if (colonIndex == -1) break

            val key = content.substring(i, colonIndex).trim().uppercase()
            i = colonIndex + 1

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
            // Keep first instance
            if (!fields.containsKey(key)) {
                fields[key] = sb.toString()
            }
        }

        val ssid = fields["S"] ?: ""
        val password = fields["P"] ?: ""
        val securityRaw = (fields["T"] ?: "nopass").trim().uppercase()
        val hiddenStr = fields["H"]?.trim()?.lowercase()
        val isHidden = hiddenStr == "true" || hiddenStr == "1"

        if (ssid.isBlank()) {
            return WiFiNetworkData(
                ssid = "",
                isValid = false,
                validationError = "Missing network name (SSID)"
            )
        }

        // Validate security type
        val normalizedSecurity = when (securityRaw) {
            "WPA", "WPA2", "WPA/WPA2" -> "WPA"
            "WPA3", "SAE" -> "WPA3"
            "NOPASS", "", "NONE", "OPEN" -> "nopass"
            "WEP" -> {
                return WiFiNetworkData(
                    ssid = ssid,
                    password = password,
                    security = "WEP",
                    isHidden = isHidden,
                    isValid = false,
                    validationError = "WEP security is obsolete and unsupported on modern Android devices"
                )
            }
            else -> {
                return WiFiNetworkData(
                    ssid = ssid,
                    password = password,
                    security = securityRaw,
                    isHidden = isHidden,
                    isValid = false,
                    validationError = "Unsupported Wi-Fi security type: $securityRaw"
                )
            }
        }

        // Validate password requirements for secured networks
        if (normalizedSecurity == "WPA" || normalizedSecurity == "WPA3") {
            if (password.isEmpty()) {
                return WiFiNetworkData(
                    ssid = ssid,
                    password = "",
                    security = normalizedSecurity,
                    isHidden = isHidden,
                    isValid = false,
                    validationError = "Password is required for secured $normalizedSecurity network"
                )
            }
            if (password.length < 8 || password.length > 63) {
                return WiFiNetworkData(
                    ssid = ssid,
                    password = password,
                    security = normalizedSecurity,
                    isHidden = isHidden,
                    isValid = false,
                    validationError = "WPA passphrase must be between 8 and 63 characters (found ${password.length})"
                )
            }
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
