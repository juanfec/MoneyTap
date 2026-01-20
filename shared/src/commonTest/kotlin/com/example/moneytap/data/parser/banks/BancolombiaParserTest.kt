package com.example.moneytap.data.parser.banks

import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BancolombiaParserTest {

    private val parser = BancolombiaParser()
    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `bankName is Bancolombia`() {
        assertEquals("Bancolombia", parser.bankName)
    }

    @Test
    fun `canHandle returns true for Bancolombia sender`() {
        assertTrue(parser.canHandle("Bancolombia"))
    }

    @Test
    fun `canHandle returns true for short code 85432`() {
        assertTrue(parser.canHandle("85432"))
    }

    @Test
    fun `canHandle returns false for unknown sender`() {
        assertEquals(false, parser.canHandle("UnknownBank"))
    }

    @Test
    fun `parse returns null for unrecognized message`() {
        val result = parser.parse("Hola, este es un mensaje normal", testTimestamp)
        assertNull(result)
    }

    @Test
    fun `parse extracts purchase with card and merchant`() {
        val sms = "Compra por \$150.000 con TC *1234 en ALMACEN EXITO. Consulte saldo: \$500.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(150000.0, result.amount)
        assertEquals(500000.0, result.balance)
        assertEquals("1234", result.cardLast4)
        assertEquals("ALMACEN EXITO", result.merchant)
        assertEquals("Compra", result.description)
        assertEquals("Bancolombia", result.bankName)
    }

    @Test
    fun `parse extracts purchase with TD card`() {
        val sms = "Compra por \$120.500 con TD *4567 en RESTAURANTE ABC"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(120500.0, result.amount)
        assertEquals("4567", result.cardLast4)
    }

    @Test
    fun `parse extracts withdrawal`() {
        val sms = "Retiro por \$200.000 con TD *5678 en CAJERO AV CHILE"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.WITHDRAWAL, result.type)
        assertEquals(200000.0, result.amount)
        assertEquals("5678", result.cardLast4)
        assertEquals("CAJERO AV CHILE", result.merchant)
        assertEquals("Retiro", result.description)
    }

    @Test
    fun `parse extracts transfer out`() {
        val sms = "Transferencia por \$500.000 a cuenta 123456789"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(500000.0, result.amount)
        assertEquals("Transferencia enviada", result.description)
    }

    @Test
    fun `parse extracts deposit`() {
        val sms = "Consignación por \$750.000 en cuenta *9012"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(750000.0, result.amount)
        assertEquals("Consignación", result.description)
    }

    @Test
    fun `parse handles amount with decimals`() {
        val sms = "Compra por \$45.678,90 con TC *9999 en TIENDA XYZ"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(45678.90, result.amount)
    }

    @Test
    fun `parse extracts reference when present`() {
        val sms = "Compra por \$10.000 con TC *1234 en TIENDA. Ref: ABC123"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals("ABC123", result.reference)
    }

    @Test
    fun `parse sets correct timestamp`() {
        val sms = "Compra por \$50.000 con TC *1234 en TIENDA"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(testTimestamp, result.timestamp)
    }

    @Test
    fun `parse sets COP as default currency`() {
        val sms = "Compra por \$50.000 con TC *1234 en TIENDA"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals("COP", result.currency)
    }

    @Test
    fun `parse extracts balance when present`() {
        val sms = "Compra por \$50.000 con TC *1234 en TIENDA. Saldo: \$450.000"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(450000.0, result.balance)
    }

    @Test
    fun `parse handles pago keyword as debit`() {
        val sms = "Pago por \$25.000 en SERVICIOS PUBLICOS"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Pago", result.description)
    }

    @Test
    fun `parse stores raw message`() {
        val sms = "Compra por \$50.000 con TC *1234 en TIENDA"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(sms, result.rawMessage)
    }
}
