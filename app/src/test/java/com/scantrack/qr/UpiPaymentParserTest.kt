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
}

