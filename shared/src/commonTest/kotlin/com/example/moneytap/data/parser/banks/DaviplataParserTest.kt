package com.example.moneytap.data.parser.banks

import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaviplataParserTest {

    private val parser = DaviplataParser()
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `bankName is Daviplata`() {
        assertEquals("Daviplata", parser.bankName)
    }

    @Test
    fun `canHandle returns true for Daviplata sender`() {
        assertTrue(parser.canHandle("Daviplata"))
    }

    @Test
    fun `canHandle returns true for Davivienda sender`() {
        assertTrue(parser.canHandle("Davivienda"))
    }

    @Test
    fun `canHandle returns true for short code 85594`() {
        assertTrue(parser.canHandle("85594"))
    }

    @Test
    fun `canHandle returns false for unknown sender`() {
        assertEquals(false, parser.canHandle("UnknownBank"))
    }

    @Test
    fun `parse returns null for unrecognized message`() {
        val result = parser.parse("Mensaje aleatorio", testTimestamp)
        assertNull(result)
    }

    @Test
    fun `parse extracts payment with merchant and balance`() {
        val sms = "Pagaste \$25.000 en RAPPI. Saldo disponible: \$175.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(25000.0, result.amount)
        assertEquals(175000.0, result.balance)
        assertEquals("RAPPI", result.merchant)
        assertEquals("Pago Daviplata", result.description)
        assertEquals("Daviplata", result.bankName)
    }

    @Test
    fun `parse extracts transfer sent with phone number masked`() {
        val sms = "Enviaste \$50.000 a 3001234567. Saldo: \$150.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(50000.0, result.amount)
        assertEquals(150000.0, result.balance)
        assertEquals("300****567", result.merchant)
        assertEquals("Transferencia enviada", result.description)
    }

    @Test
    fun `parse extracts transfer received with masked phone`() {
        val sms = "Te enviaron \$75.000 desde 3109876543. Saldo: \$225.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(75000.0, result.amount)
        assertEquals(225000.0, result.balance)
        assertEquals("310****543", result.merchant)
        assertEquals("Transferencia recibida", result.description)
    }

    @Test
    fun `parse extracts transfer received with recibiste keyword`() {
        val sms = "Recibiste \$100.000 de 3201112223. Saldo: \$300.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(100000.0, result.amount)
        assertEquals(300000.0, result.balance)
    }

    @Test
    fun `parse extracts withdrawal`() {
        val sms = "Retiraste \$120.000. Saldo: \$80.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.WITHDRAWAL, result.type)
        assertEquals(120000.0, result.amount)
        assertEquals(80000.0, result.balance)
        assertEquals("Retiro Daviplata", result.description)
    }

    @Test
    fun `parse extracts deposit`() {
        val sms = "Recargaste \$200.000. Saldo: \$280.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(200000.0, result.amount)
        assertEquals(280000.0, result.balance)
        assertEquals("Recarga Daviplata", result.description)
    }

    @Test
    fun `parse extracts balance transfer`() {
        val sms = "Transferiste \$30.000 a cuenta ahorros. Saldo: \$170.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(30000.0, result.amount)
        assertEquals(170000.0, result.balance)
        assertEquals("Transferencia de saldo", result.description)
    }

    @Test
    fun `parse handles amount with decimals`() {
        val sms = "Pagaste \$45.678,90 en COMERCIO. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(45678.90, result.amount)
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
    fun `parse sets COP as default currency`() {
        val sms = "Pagaste \$50.000 en TIENDA. Saldo: \$100.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals("COP", result.currency)
    }

    @Test
    fun `parse handles message without balance`() {
        val sms = "Pagaste \$25.000 en ALMACEN"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(25000.0, result.amount)
        assertNull(result.balance)
    }
}
