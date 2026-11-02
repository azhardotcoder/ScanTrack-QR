package com.scantrack.qr

import com.scantrack.qr.data.local.dao.QrDao
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import com.scantrack.qr.presentation.scanner.ScanState
import com.scantrack.qr.presentation.scanner.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerStateMachineTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeDao = object : QrDao {
        val records = mutableListOf<QrEntity>()
        override suspend fun insertQr(qr: QrEntity): Long {
            records.add(qr)
            return records.size.toLong()
        }
        override suspend fun updateQr(qr: QrEntity) {}
        override suspend fun deleteQr(qr: QrEntity) {}
        override suspend fun getQrById(id: Long): QrEntity? = records.find { it.id == id }
        override suspend fun getQrByRawValue(rawValue: String): QrEntity? = records.find { it.rawValue == rawValue }
        override fun getPinnedQrs(): Flow<List<QrEntity>> = flowOf(emptyList())
        override fun getRecentQrs(): Flow<List<QrEntity>> = flowOf(emptyList())
        override fun getQuickAccessQrs(): Flow<List<QrEntity>> = flowOf(emptyList())
        override fun getAllQrs(): Flow<List<QrEntity>> = flowOf(records)
        override suspend fun deleteById(id: Long): Int = 1
        override suspend fun deleteAll(): Int = records.size.also { records.clear() }
        override suspend fun updatePin(id: Long, isPinned: Boolean): Int = 1
        override suspend fun togglePinById(id: Long): Int = 1
        override suspend fun updateLabel(id: Long, label: String): Int = 1
        override suspend fun incrementUsage(id: Long, timestamp: Long): Int = 1
        override suspend fun incrementUsageByRawValue(rawValue: String, timestamp: Long): Int {
            val found = records.find { it.rawValue == rawValue }
            return if (found != null) 1 else 0
        }
        override fun searchAndFilterQrs(query: String, typeFilter: String?): Flow<List<QrEntity>> = flowOf(emptyList())
    }

    private lateinit var repository: QrRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = QrRepository(fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isScanning() {
        val viewModel = ScannerViewModel(repository = repository, hapticFlowOverride = flowOf(true))
        assertTrue(viewModel.scanState.value is ScanState.Scanning)
    }

    @Test
    fun onQrDetected_transitionsToResult_andIgnoresSubsequentDetectionsUntilDismissed() = runTest(testDispatcher) {
        val viewModel = ScannerViewModel(repository = repository, hapticFlowOverride = flowOf(true))

        // Initial scan
        viewModel.onQrDetected("https://first-scan.com")
        testScheduler.advanceUntilIdle()

        val firstState = viewModel.scanState.value
        assertTrue(firstState is ScanState.Result)
        assertEquals("https://first-scan.com", (firstState as ScanState.Result).result.rawValue)

        // Rapid concurrent/subsequent scan while result is already displayed
        viewModel.onQrDetected("https://second-scan-should-be-ignored.com")
        testScheduler.advanceUntilIdle()

        // Verify result was NOT replaced
        val stateAfterSecond = viewModel.scanState.value
        assertTrue(stateAfterSecond is ScanState.Result)
        assertEquals("https://first-scan.com", (stateAfterSecond as ScanState.Result).result.rawValue)

        // Dismiss result
        viewModel.dismissResult()
        assertTrue(viewModel.scanState.value is ScanState.Scanning)

        // Now new scan should be accepted
        viewModel.onQrDetected("https://third-scan-accepted.com")
        testScheduler.advanceUntilIdle()

        val thirdState = viewModel.scanState.value
        assertTrue(thirdState is ScanState.Result)
        assertEquals("https://third-scan-accepted.com", (thirdState as ScanState.Result).result.rawValue)
    }

    @Test
    fun asyncGalleryResult_withStaleSessionToken_isDroppedAfterReset() = runTest(testDispatcher) {
        val viewModel = ScannerViewModel(repository = repository, hapticFlowOverride = flowOf(true))

        // Capture session token before async task
        val oldSessionToken = viewModel.getCurrentSessionToken()

        // User leaves / resets scanner
        viewModel.resetScanner()

        // Async result arrives late from background thread with old session token
        viewModel.onQrDetected("https://late-gallery-result.com", sessionToken = oldSessionToken)
        testScheduler.advanceUntilIdle()

        // Verify late result was ignored and scanner is still scanning
        assertTrue(viewModel.scanState.value is ScanState.Scanning)
        assertFalse(viewModel.scanState.value is ScanState.Result)
    }
}
