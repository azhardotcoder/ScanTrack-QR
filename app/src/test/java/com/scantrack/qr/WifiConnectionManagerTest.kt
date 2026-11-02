package com.scantrack.qr

import android.net.ConnectivityManager
import com.scantrack.qr.presentation.utils.WifiConnectionManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiConnectionManagerTest {

    private val dummyCallback = object : ConnectivityManager.NetworkCallback() {}

    @Before
    fun setUp() {
        WifiConnectionManager.resetForTesting()
    }

    @After
    fun tearDown() {
        WifiConnectionManager.resetForTesting()
    }

    @Test
    fun connect_initiatesNewSession_withInitialUnconnectedState() {
        WifiConnectionManager.initiateSession(
            requestId = 1L,
            ssid = "OfficeNet",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        val session = WifiConnectionManager.currentSession
        assertNotNull(session)
        assertEquals(1L, session?.requestId)
        assertEquals("OfficeNet", session?.ssid)
        assertFalse(session?.isConnected ?: true)
    }

    @Test
    fun handleAvailable_withMatchingRequestId_transitionsToConnected() {
        var statusMessage: String? = null
        WifiConnectionManager.initiateSession(
            requestId = 1L,
            ssid = "OfficeNet",
            callback = dummyCallback,
            timeoutRunnable = Runnable {}
        )

        val handled = WifiConnectionManager.handleAvailable(1L) { statusMessage = it }

        assertTrue(handled)
        assertTrue(WifiConnectionManager.currentSession?.isConnected == true)
        assertNull("Timeout runnable must be cleared after successful connection", WifiConnectionManager.currentSession?.timeoutRunnable)
        assertEquals("Connected to OfficeNet", statusMessage)
    }

    @Test
    fun staleCallback_fromSupersededSession_isSuppressedAndPreservesActiveSession() {
        // Step 1: Request 1 is initiated
        WifiConnectionManager.initiateSession(
            requestId = 1L,
            ssid = "FirstNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        // Step 2: User requests another network before Request 1 completes (superseding it)
        WifiConnectionManager.initiateSession(
            requestId = 2L,
            ssid = "SecondNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        // Step 3: Stale onUnavailable/onLost arrives from the first request (requestId = 1)
        var statusReported: String? = null
        val handledStale = WifiConnectionManager.handleUnavailable(1L) { statusReported = it }

        assertFalse("Stale callback must be ignored", handledStale)
        assertNull("No error status should be broadcast for stale superseded request", statusReported)

        // Verify active session for SecondNetwork was NOT destroyed
        val activeSession = WifiConnectionManager.currentSession
        assertNotNull("Active session must remain intact", activeSession)
        assertEquals(2L, activeSession?.requestId)
        assertEquals("SecondNetwork", activeSession?.ssid)
    }

    @Test
    fun staleAvailable_fromSupersededSession_isIgnored() {
        WifiConnectionManager.initiateSession(
            requestId = 1L,
            ssid = "OldNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        WifiConnectionManager.initiateSession(
            requestId = 2L,
            ssid = "ActiveNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        val handledStale = WifiConnectionManager.handleAvailable(1L)
        assertFalse(handledStale)

        val activeSession = WifiConnectionManager.currentSession
        assertEquals(2L, activeSession?.requestId)
        assertFalse(activeSession?.isConnected ?: true)
    }

    @Test
    fun handleUnavailable_withActiveSession_clearsSession() {
        var statusMessage: String? = null
        WifiConnectionManager.initiateSession(
            requestId = 42L,
            ssid = "FailingNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        val handled = WifiConnectionManager.handleUnavailable(42L) { statusMessage = it }

        assertTrue(handled)
        assertNull("Active session must be cleared on disconnect/failure", WifiConnectionManager.currentSession)
        assertEquals("Could not connect to FailingNetwork", statusMessage)
    }

    @Test
    fun handleLost_withActiveSession_clearsSession() {
        var statusMessage: String? = null
        WifiConnectionManager.initiateSession(
            requestId = 99L,
            ssid = "HomeNetwork",
            callback = dummyCallback,
            timeoutRunnable = null
        )

        val handled = WifiConnectionManager.handleLost(99L) { statusMessage = it }

        assertTrue(handled)
        assertNull("Active session must be cleared on network loss", WifiConnectionManager.currentSession)
        assertEquals("Disconnected from HomeNetwork", statusMessage)
    }
}

