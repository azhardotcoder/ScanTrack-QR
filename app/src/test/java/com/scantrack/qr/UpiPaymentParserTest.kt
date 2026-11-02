package com.scantrack.qr

import com.scantrack.qr.data.parser.UpiPaymentParser
import org.junit.Assert.*
import org.junit.Test

class UpiPaymentParserTest {

    @Test
    fun parse_validPersonalUpi_extractsFieldsAndPreservesOriginal() {
        val raw = "upi://pay?pa=friend@okhdfcbank&pn=John%20Doe&am=150.00&cu=INR"
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri)
        assertEquals("friend@okhdfcbank", result.payeeVpa)
        assertEquals("John Doe", result.payeeName)
        assertEquals("150.00", result.amount)
        assertEquals("INR", result.currency)
    }

    @Test
    fun parse_merchantUpiWithMcAndTr_neverStripsParameters() {
        val raw = "upi://pay?pa=store@icici&pn=MegaStore&mc=5411&tr=TXN998877&mode=02&am=1299.50&cu=INR"
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri) // strictly preserved
        assertEquals("store@icici", result.payeeVpa)
        assertEquals("MegaStore", result.payeeName)
        assertEquals("5411", result.merchantCode)
        assertEquals("TXN998877", result.transactionRef)
        assertEquals("1299.50", result.amount)
    }

    @Test
    fun parse_missingPayeeVpa_returnsInvalid() {
        val raw = "upi://pay?pn=Unknown&am=100.00"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Missing payee UPI ID") == true)
    }

    @Test
    fun parse_malformedPayeeVpaWithoutAtSymbol_returnsInvalid() {
        val raw = "upi://pay?pa=invalid_vpa_address&pn=Store"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Malformed payee UPI ID") == true)
    }

    @Test
    fun parse_negativeAmount_returnsInvalid() {
        val raw = "upi://pay?pa=user@upi&pn=Test&am=-50.00"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Invalid transaction amount") == true)
    }

    @Test
    fun parse_scientificNotationAmount_rejectsWithStrictValidation() {
        val raw = "upi://pay?pa=user@upi&pn=Test&am=1e5"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("no scientific notation") == true)
    }

    @Test
    fun parse_excessiveDecimalPlaces_returnsInvalid() {
        val raw = "upi://pay?pa=user@upi&pn=Test&am=10.555"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("up to 2 decimal places") == true)
    }

    @Test
    fun parse_overflowAmount_returnsInvalid() {
        val raw = "upi://pay?pa=user@upi&pn=Test&am=999999999999.00"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Invalid transaction amount") == true)
    }

    @Test
    fun parse_malformedDecimal_missingLeadingDigit_returnsInvalid() {
        val raw = "upi://pay?pa=user@upi&pn=Test&am=.50"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("Invalid transaction amount") == true)
    }

    @Test
    fun parse_unsupportedScheme_returnsInvalid() {
        val raw = "https://pay.google.com/upi"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("expected strict upi://pay") == true)
    }

    @Test
    fun parse_duplicateQueryParameters_rejectsAmbiguousPaymentInstruction() {
        val raw = "upi://pay?pa=user@upi&pa=other@upi&pn=First"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("duplicate 'pa' parameter") == true)
    }

    @Test
    fun parse_duplicateAmountParameters_rejectsAmbiguousPaymentInstruction() {
        val raw = "upi://pay?pa=user@upi&pn=First&am=100&am=200"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("duplicate 'am' parameter") == true)
    }

    @Test
    fun parse_permissivePrefixWithoutHierarchicalAuthority_returnsInvalid() {
        val raw = "upi:pay?pa=user@upi&pn=Test"
        val result = UpiPaymentParser.parse(raw)

        assertFalse(result.isValid)
        assertTrue(result.validationError?.contains("expected strict upi://pay") == true)
    }

    @Test
    fun parse_personalUpiQr_identifiesP2P_preservesOriginal() {
        val raw = "upi://pay?pa=friend@okhdfcbank&pn=John%20Doe&am=100.00&cu=INR"
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri)
        assertFalse(result.isMerchant)
        assertEquals("friend@okhdfcbank", result.payeeVpa)
        assertEquals("John Doe", result.payeeName)
        assertEquals("100.00", result.amount)
    }

    @Test
    fun parse_merchantStaticUpiQr_withMode01_identifiesMerchantAndPreservesParameters() {
        val raw = "upi://pay?pa=merchant@icici&pn=Corner%20Store&mc=5411&mode=01&orgid=180001"
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri)
        assertTrue(result.isMerchant)
        assertEquals("5411", result.merchantCode)
        assertEquals("01", result.mode)
        assertEquals("180001", result.orgId)
    }

    @Test
    fun parse_merchantDynamicPosQr_withMode02AndTid_preservesAllOriginalParameters() {
        val raw = "upi://pay?pa=pos@hdfcbank&pn=Retail%20Outlet&mc=5311&tr=TXN123456789&tid=TERM001&mode=02&am=499.00&cu=INR"
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri)
        assertTrue(result.isMerchant)
        assertEquals("5311", result.merchantCode)
        assertEquals("TXN123456789", result.transactionRef)
        assertEquals("TERM001", result.terminalId)
        assertEquals("02", result.mode)
        assertEquals("499.00", result.amount)
    }

    @Test
    fun parse_merchantSignedQr_withSignAndOrgid_neverStripsSignatureOrAltersUri() {
        val raw = "upi://pay?pa=secure@axisbank&pn=SuperStore&mc=5499&mode=02&orgid=189999&sign=MEYCIQC3eXSampleSignatureData1234567890abcdef=="
        val result = UpiPaymentParser.parse(raw)

        assertTrue(result.isValid)
        assertEquals(raw, result.originalUri)
        assertTrue(result.isMerchant)
        assertEquals("189999", result.orgId)
    }
}
