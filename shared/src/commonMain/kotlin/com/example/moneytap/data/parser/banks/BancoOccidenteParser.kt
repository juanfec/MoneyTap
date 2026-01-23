package com.example.moneytap.data.parser.banks

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.data.parser.BankSmsParser
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Parser for Banco de Occidente transaction SMS messages.
 *
 * Example formats:
 * - "Ud realizo una compra en SUPERM MAS POR MENOS C por $11.440.T. Credencial *4115, 2026/01/19, 18:26."
 * - "Ud realizo un retiro por $200.000 en CAJERO..."
 */
class BancoOccidenteParser : BankSmsParser {

    override val bankName: String = "Banco de Occidente"

    override val senderIds: List<String> = listOf(
        "85722",
        "Bco. Occidente",
        "BcoOccidente",
    )

    // Amount pattern: $11.440 or $1.500.000 (may have trailing .T or similar artifacts)
    private val amountPattern = Regex("""\$\s*([\d.,]+)""")

    // Card pattern: Credencial *4115 or tarjeta *1234
    private val cardPattern = Regex(
        """(?:Credencial|tarjeta|\*)\s*\*?(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    // Merchant pattern: "en MERCHANT por" or "en MERCHANT."
    private val merchantPattern = Regex(
        """\ben\s+(.+?)\s+por\s""",
        RegexOption.IGNORE_CASE,
    )

    // Date pattern: 2026/01/19 or 19/01/2026
    private val datePattern = Regex("""(\d{4}/\d{2}/\d{2}|\d{2}/\d{2}/\d{4})""")

    override fun parse(smsBody: String, timestamp: Instant): TransactionInfo? {
        // Extract the transaction amount
        val amountMatch = amountPattern.find(smsBody) ?: return null
        val amountStr = amountMatch.groupValues[1]
            .replace(Regex("""[A-Za-z]+$"""), "") // Remove trailing letters like ".T"
            .trimEnd('.')
        val amount = AmountParser.parseColombianAmount(amountStr) ?: return null

        // Determine transaction type
        val type = determineTransactionType(smsBody) ?: return null

        // Extract card number
        val cardLast4 = cardPattern.find(smsBody)?.groupValues?.get(1)

        // Extract merchant
        val merchant = extractMerchant(smsBody, type)

        // Build description
        val description = buildDescription(type)

        return TransactionInfo(
            type = type,
            amount = amount,
            balance = null,
            cardLast4 = cardLast4,
            merchant = merchant,
            description = description,
            reference = null,
            bankName = bankName,
            timestamp = timestamp,
            rawMessage = smsBody,
        )
    }

    private fun determineTransactionType(smsBody: String): TransactionType? {
        val lowerBody = smsBody.lowercase()
        return when {
            lowerBody.contains("compra") -> TransactionType.DEBIT
            lowerBody.contains("pago") || lowerBody.contains("pagaste") ||
                lowerBody.contains("pagó") -> TransactionType.DEBIT
            lowerBody.contains("retiro") || lowerBody.contains("retiraste") -> TransactionType.WITHDRAWAL
            lowerBody.contains("transferencia") || lowerBody.contains("transferiste") -> {
                if (lowerBody.contains("recibiste") || lowerBody.contains("recibió")) {
                    TransactionType.CREDIT
                } else {
                    TransactionType.TRANSFER
                }
            }
            lowerBody.contains("consignación") || lowerBody.contains("consignacion") ||
                lowerBody.contains("abono") || lowerBody.contains("recibiste") -> TransactionType.CREDIT
            else -> null
        }
    }

    private fun extractMerchant(smsBody: String, type: TransactionType): String? {
        return when (type) {
            TransactionType.DEBIT, TransactionType.WITHDRAWAL -> {
                merchantPattern.find(smsBody)?.groupValues?.get(1)?.trim()
            }
            else -> null
        }
    }

    private fun buildDescription(type: TransactionType): String {
        return when (type) {
            TransactionType.DEBIT -> "Compra"
            TransactionType.WITHDRAWAL -> "Retiro"
            TransactionType.TRANSFER -> "Transferencia"
            TransactionType.CREDIT -> "Abono"
        }
    }
}
