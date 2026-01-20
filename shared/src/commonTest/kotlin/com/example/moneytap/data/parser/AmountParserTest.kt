package com.example.moneytap.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountParserTest {

    @Test
    fun `parseColombianAmount parses simple number`() {
        val result = AmountParser.parseColombianAmount("1234567")
        assertEquals(1234567.0, result)
    }

    @Test
    fun `parseColombianAmount parses number with thousands separators`() {
        val result = AmountParser.parseColombianAmount("1.234.567")
        assertEquals(1234567.0, result)
    }

    @Test
    fun `parseColombianAmount parses number with decimals`() {
        val result = AmountParser.parseColombianAmount("1.234.567,89")
        assertEquals(1234567.89, result)
    }

    @Test
    fun `parseColombianAmount parses number with only decimal separator`() {
        val result = AmountParser.parseColombianAmount("1234,50")
        assertEquals(1234.50, result)
    }

    @Test
    fun `parseColombianAmount handles dollar sign`() {
        val result = AmountParser.parseColombianAmount("$1.234.567")
        assertEquals(1234567.0, result)
    }

    @Test
    fun `parseColombianAmount handles spaces`() {
        val result = AmountParser.parseColombianAmount("$ 1.234.567")
        assertEquals(1234567.0, result)
    }

    @Test
    fun `parseColombianAmount returns null for invalid input`() {
        val result = AmountParser.parseColombianAmount("abc")
        assertNull(result)
    }

    @Test
    fun `parseColombianAmount returns null for empty string`() {
        val result = AmountParser.parseColombianAmount("")
        assertNull(result)
    }

    @Test
    fun `extractAmount extracts amount from text`() {
        val result = AmountParser.extractAmount("compra por $50.000 en tienda")
        assertEquals(50000.0, result)
    }

    @Test
    fun `extractAmount extracts amount with decimals`() {
        val result = AmountParser.extractAmount("pagaste $123.456,78")
        assertEquals(123456.78, result)
    }

    @Test
    fun `extractAmount returns null when no amount found`() {
        val result = AmountParser.extractAmount("mensaje sin montos")
        assertNull(result)
    }
}
