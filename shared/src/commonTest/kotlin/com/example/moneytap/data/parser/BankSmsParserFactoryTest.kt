package com.example.moneytap.data.parser

import com.example.moneytap.data.parser.banks.BancolombiaParser
import com.example.moneytap.data.parser.banks.DaviplataParser
import com.example.moneytap.data.parser.banks.NequiParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankSmsParserFactoryTest {

    @Test
    fun `getParser returns BancolombiaParser for Bancolombia sender`() {
        val parser = BankSmsParserFactory.getParser("Bancolombia")
        assertNotNull(parser)
        assertIs<BancolombiaParser>(parser)
    }

    @Test
    fun `getParser returns BancolombiaParser for 85432 short code`() {
        val parser = BankSmsParserFactory.getParser("85432")
        assertNotNull(parser)
        assertIs<BancolombiaParser>(parser)
    }

    @Test
    fun `getParser returns NequiParser for Nequi sender`() {
        val parser = BankSmsParserFactory.getParser("Nequi")
        assertNotNull(parser)
        assertIs<NequiParser>(parser)
    }

    @Test
    fun `getParser returns NequiParser for 85954 short code`() {
        val parser = BankSmsParserFactory.getParser("85954")
        assertNotNull(parser)
        assertIs<NequiParser>(parser)
    }

    @Test
    fun `getParser returns DaviplataParser for Daviplata sender`() {
        val parser = BankSmsParserFactory.getParser("Daviplata")
        assertNotNull(parser)
        assertIs<DaviplataParser>(parser)
    }

    @Test
    fun `getParser returns DaviplataParser for Davivienda sender`() {
        val parser = BankSmsParserFactory.getParser("Davivienda")
        assertNotNull(parser)
        assertIs<DaviplataParser>(parser)
    }

    @Test
    fun `getParser returns DaviplataParser for 85594 short code`() {
        val parser = BankSmsParserFactory.getParser("85594")
        assertNotNull(parser)
        assertIs<DaviplataParser>(parser)
    }

    @Test
    fun `getParser returns null for unknown sender`() {
        val parser = BankSmsParserFactory.getParser("UnknownBank")
        assertNull(parser)
    }

    @Test
    fun `getParser is case insensitive`() {
        assertNotNull(BankSmsParserFactory.getParser("BANCOLOMBIA"))
        assertNotNull(BankSmsParserFactory.getParser("bancolombia"))
        assertNotNull(BankSmsParserFactory.getParser("NEQUI"))
        assertNotNull(BankSmsParserFactory.getParser("nequi"))
    }

    @Test
    fun `getParser handles partial matches`() {
        assertNotNull(BankSmsParserFactory.getParser("Mensaje de Bancolombia"))
        assertNotNull(BankSmsParserFactory.getParser("Info Nequi"))
    }

    @Test
    fun `getAllParsers returns all registered parsers`() {
        val parsers = BankSmsParserFactory.getAllParsers()
        assertEquals(3, parsers.size)
    }

    @Test
    fun `getSupportedBanks returns all bank names`() {
        val banks = BankSmsParserFactory.getSupportedBanks()
        assertEquals(3, banks.size)
        assertTrue(banks.contains("Bancolombia"))
        assertTrue(banks.contains("Nequi"))
        assertTrue(banks.contains("Daviplata"))
    }

    @Test
    fun `getAllSenderIds returns all sender IDs`() {
        val senderIds = BankSmsParserFactory.getAllSenderIds()
        assertTrue(senderIds.contains("Bancolombia"))
        assertTrue(senderIds.contains("85432"))
        assertTrue(senderIds.contains("Nequi"))
        assertTrue(senderIds.contains("85954"))
        assertTrue(senderIds.contains("Daviplata"))
        assertTrue(senderIds.contains("85594"))
    }
}
