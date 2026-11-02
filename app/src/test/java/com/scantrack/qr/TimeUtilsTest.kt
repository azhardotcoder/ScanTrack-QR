package com.scantrack.qr

import com.scantrack.qr.presentation.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeUtilsTest {

    private val baseTime = 1700000000000L // arbitrary fixed time point

    @Test
    fun formatRelativeTime_momentsAgo_returnsJustNow() {
        val timestamp = baseTime - 15_000L // 15 seconds ago
        assertEquals("just now", TimeUtils.formatRelativeTime(timestamp, baseTime))
    }

    @Test
    fun formatRelativeTime_fewMinutesAgo_returnsMinutesAgo() {
        val timestamp = baseTime - 300_000L // 5 minutes ago
        assertEquals("5m ago", TimeUtils.formatRelativeTime(timestamp, baseTime))
    }

    @Test
    fun formatRelativeTime_fewHoursAgo_returnsHoursAgo() {
        val timestamp = baseTime - 7_200_000L // 2 hours ago
        assertEquals("2h ago", TimeUtils.formatRelativeTime(timestamp, baseTime))
    }

    @Test
    fun formatRelativeTime_oneDayAgo_returnsYesterday() {
        val timestamp = baseTime - 90_000_000L // 25 hours ago
        assertEquals("yesterday", TimeUtils.formatRelativeTime(timestamp, baseTime))
    }

    @Test
    fun formatRelativeTime_severalDaysAgo_returnsCalendarDate() {
        val timestamp = baseTime - (5 * 86_400_000L) // 5 days ago
        val expected = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        assertEquals(expected, TimeUtils.formatRelativeTime(timestamp, baseTime))
    }

    @Test
    fun formatRelativeTime_futureTimestamp_returnsJustNowGracefully() {
        val futureTime = baseTime + 10_000L
        assertEquals("just now", TimeUtils.formatRelativeTime(futureTime, baseTime))
    }
}

