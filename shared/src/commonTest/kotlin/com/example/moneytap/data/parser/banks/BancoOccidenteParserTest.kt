package com.example.moneytap.data.parser.banks

import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BancoOccidenteParserTest {

    private val parser = BancoOccidenteParser()
    private val testTimestamp = Instant.parse("2026-01-19T18:26:00Z")

    @Test
    fun `bankName is Banco de Occidente`() {
        assertEquals("Banco de Occidente", parser.bankName)
    }

    @Test
    fun `canHandle returns true for sender 85722`() {
        assertTrue(parser.canHandle("85722"))
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
    fun `parse extracts purchase at SUPERM MAS POR MENOS with trailing T`() {
        val sms = "Ud realizo una compra en SUPERM MAS POR MENOS C por \$11.440.T. Credencial *4115, 2026/01/19, 18:26."
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(11440.0, result.amount)
        assertEquals("4115", result.cardLast4)
        assertEquals("SUPERM MAS POR MENOS C", result.merchant)
        assertEquals("Compra", result.description)
        assertEquals("Banco de Occidente", result.bankName)
    }

    @Test
    fun `parse handles standard purchase format`() {
        val sms = "Ud realizo una compra en TIENDA ABC por \$50.000. Credencial *1234, 2026/01/15, 10:30."
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(50000.0, result.amount)
        assertEquals("1234", result.cardLast4)
        assertEquals("TIENDA ABC", result.merchant)
    }

    @Test
    fun `parse extracts withdrawal`() {
        val sms = "Ud realizo un retiro por \$200.000 en CAJERO CENTRO. Credencial *5678"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(TransactionType.WITHDRAWAL, result.type)
        assertEquals(200000.0, result.amount)
        assertEquals("5678", result.cardLast4)
    }

    @Test
    fun `parse handles amount with decimals`() {
        val sms = "Ud realizo una compra en FARMACIA por \$25.500,50. Credencial *9999"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(25500.50, result.amount)
    }

    @Test
    fun `parse sets correct timestamp`() {
        val sms = "Ud realizo una compra en TIENDA por \$10.000. Credencial *1234"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(testTimestamp, result.timestamp)
    }

    @Test
    fun `parse stores raw message`() {
        val sms = "Ud realizo una compra en TIENDA por \$10.000. Credencial *1234"
        val result = parser.parse(sms, testTimestamp)

        assertNotNull(result)
        assertEquals(sms, result.rawMessage)
    }
}
