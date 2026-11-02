package com.scantrack.qr

import com.scantrack.qr.data.parser.WiFiQrParser
import org.junit.Assert.*
import org.junit.Test

class WiFiQrParserTest {

    @Test
    fun parse_standardWpaNetwork_returnsValidData() {
        val raw = "WIFI:T:WPA;S:OfficeNet;P:supersecret123;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("OfficeNet", result.ssid)
        assertEquals("supersecret123", result.password)
        assertEquals("WPA", result.security)
        assertFalse(result.isHidden)
    }

    @Test
    fun parse_openNetwork_returnsValidNopass() {
        val raw = "WIFI:T:nopass;S:CoffeeShop_Guest;P:;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("CoffeeShop_Guest", result.ssid)
        assertEquals("", result.password)
        assertEquals("nopass", result.security)
        assertFalse(result.isHidden)
    }

    @Test
    fun parse_escapedSemicolon_correctlyParsesSsidAndPassword() {
        val raw = "WIFI:T:WPA;S:My\\;Network;P:Secret\\;Password123;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("My;Network", result.ssid)
        assertEquals("Secret;Password123", result.password)
    }

    @Test
    fun parse_escapedBackslashAndColon_correctlyDecodes() {
        val raw = "WIFI:T:WPA;S:Domain\\\\Net;P:Admin\\:12345;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("Domain\\Net", result.ssid)
        assertEquals("Admin:12345", result.password)
    }

    @Test
    fun parse_hiddenSsid_returnsHiddenTrue() {
        val raw = "WIFI:T:WPA;S:SecretHQ;P:passphrase123;H:true;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("SecretHQ", result.ssid)
        assertTrue(result.isHidden)
    }

    @Test
    fun parse_wpa3Network_normalizesToWpa3() {
        val raw = "WIFI:T:WPA3;S:ModernRouter;P:securekey123;;"
        val result = WiFiQrParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals("WPA3", result.security)
    }

    @Test
    fun parse_missingSsid_returnsInvalid() {
        val raw = "WIFI:T:WPA;P:somepassword;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("SSID") == true)
    }

    @Test
    fun parse_shortWpaPassword_returnsInvalid() {
        val raw = "WIFI:T:WPA;S:ShortPassNet;P:12345;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("between 8 and 63 characters") == true)
    }

    @Test
    fun parse_wepSecurity_returnsInvalidBecauseDeprecatedAndInsecure() {
        val raw = "WIFI:T:WEP;S:LegacyRouter;P:1234567890;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("WEP security is obsolete") == true)
    }

    @Test
    fun parse_unknownSecurityType_returnsInvalid() {
        val raw = "WIFI:T:CUSTOM_AUTH;S:UnknownNet;P:12345678;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Unsupported Wi-Fi security type") == true)
    }

    @Test
    fun parse_securedWpaWithEmptyPassword_returnsInvalid() {
        val raw = "WIFI:T:WPA;S:EmptyPassNet;P:;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Password is required") == true)
    }

    @Test
    fun parse_securedWpaWithOmittedPassword_returnsInvalid() {
        val raw = "WIFI:T:WPA;S:NoPassParamNet;;"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Password is required") == true)
    }

    @Test
    fun parse_nonWifiContent_returnsInvalid() {
        val raw = "https://example.com"
        val result = WiFiQrParser.parse(raw)

        assertFalse(result.isValid)
    }
}

