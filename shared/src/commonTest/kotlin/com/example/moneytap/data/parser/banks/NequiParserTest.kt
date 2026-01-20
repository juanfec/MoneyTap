package com.example.moneytap.data.parser.banks

import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NequiParserTest {

    private val parser = NequiParser()
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `bankName is Nequi`() {
        assertEquals("Nequi", parser.bankName)
    }

    @Test
    fun `canHandle returns true for Nequi sender`() {
        assertTrue(parser.canHandle("Nequi"))
    }

    @Test
    fun `canHandle returns true for short code 85954`() {
        assertTrue(parser.canHandle("85954"))
    }

    @Test
    fun `canHandle returns false for unknown sender`() {
        assertEquals(false, parser.canHandle("UnknownBank"))
    }

    @Test
    fun `parse returns null for unrecognized message`() {
        val result = parser.parse("Mensaje sin transaccion", testTimestamp)
        assertNull(result)
    }

    @Test
    fun `parse extracts payment with merchant and balance`() {
        val sms = "Pagaste \$35.000 en RAPPI COLOMBIA. Saldo: \$165.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(35000.0, result.amount)
        assertEquals(165000.0, result.balance)
        assertEquals("RAPPI COLOMBIA", result.merchant)
        assertEquals("Pago Nequi", result.description)
        assertEquals("Nequi", result.bankName)
    }

    @Test
    fun `parse extracts transfer sent with recipient and balance`() {
        val sms = "Enviaste \$50.000 a Juan Perez. Saldo: \$200.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(50000.0, result.amount)
        assertEquals(200000.0, result.balance)
        assertEquals("Juan Perez", result.merchant)
        assertEquals("Transferencia enviada", result.description)
    }

    @Test
    fun `parse extracts transfer received with sender`() {
        val sms = "Recibiste \$100.000 de Maria Garcia. Saldo: \$300.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(100000.0, result.amount)
        assertEquals(300000.0, result.balance)
        assertEquals("Maria Garcia", result.merchant)
        assertEquals("Transferencia recibida", result.description)
    }

    @Test
    fun `parse extracts withdrawal`() {
        val sms = "Retiraste \$80.000. Saldo: \$120.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.WITHDRAWAL, result.type)
        assertEquals(80000.0, result.amount)
        assertEquals(120000.0, result.balance)
        assertEquals("Retiro Nequi", result.description)
    }

    @Test
    fun `parse extracts deposit`() {
        val sms = "Recargaste \$150.000. Saldo: \$350.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(150000.0, result.amount)
        assertEquals(350000.0, result.balance)
        assertEquals("Recarga Nequi", result.description)
    }

    @Test
    fun `parse handles amount with decimals`() {
        val sms = "Pagaste \$99.999,50 en COMERCIO. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(99999.50, result.amount)
    }

    @Test
    fun `parse sets correct timestamp`() {
        val sms = "Pagaste \$50.000 en TIENDA. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(testTimestamp, result.timestamp)
    }

    @Test
    fun `parse stores raw message`() {
        val sms = "Pagaste \$50.000 en TIENDA. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(sms, result.rawMessage)
    }

    @Test
    fun `parse handles message without balance`() {
        val sms = "Pagaste \$25.000 en ALMACEN"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(25000.0, result.amount)
        assertNull(result.balance)
    }

    @Test
    fun `parse sets COP as default currency`() {
        val sms = "Pagaste \$50.000 en TIENDA. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals("COP", result.currency)
    }
}
