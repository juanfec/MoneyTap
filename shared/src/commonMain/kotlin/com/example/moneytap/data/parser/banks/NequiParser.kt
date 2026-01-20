package com.example.moneytap.data.parser.banks

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.data.parser.BankSmsParser
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Parser for Nequi digital wallet transaction SMS messages.
 *
 * Example formats:
 * - "Enviaste $50.000 a Juan Perez. Saldo: $200.000"
 * - "Recibiste $100.000 de Maria Garcia. Saldo: $300.000"
 * - "Pagaste $35.000 en RAPPI COLOMBIA. Saldo: $165.000"
 * - "Retiraste $80.000. Saldo: $120.000"
 * - "Recargaste $150.000. Saldo: $350.000"
 */
class NequiParser : BankSmsParser {

    override val bankName: String = "Nequi"

    override val senderIds: List<String> = listOf(
        "Nequi",
        "85954",
    )

    // Colombian amount pattern: $50.000 or $1.500.000,50
    private val amountPattern = Regex("""\$\s*([\d.,]+)""")

    // Transaction type patterns (Spanish keywords)
    private val debitKeywords = Regex(
        """(compra|d[eé]bito|pago|retiro|enviaste|pagaste)""",
        RegexOption.IGNORE_CASE,
    )
    private val creditKeywords = Regex(
        """(abono|consignaci[oó]n|dep[oó]sito|recibiste|te\s+lleg[oó]|recargaste)""",
        RegexOption.IGNORE_CASE,
    )

    // Balance pattern
    private val balancePattern = Regex(
        """(?:saldo|disponible)[:\s]*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE,
    )

    // Merchant pattern: "en MERCHANT" or "en MERCHANT."
    private val merchantPattern = Regex("""\ben\s+([^.]+)""", RegexOption.IGNORE_CASE)

    // Recipient pattern: "a NAME" (for transfers)
    private val recipientPattern = Regex("""\ba\s+([^.]+)""", RegexOption.IGNORE_CASE)

    // Sender pattern: "de NAME" (for received transfers)
    private val senderPattern = Regex("""\bde\s+([^.]+)""", RegexOption.IGNORE_CASE)

    override fun parse(smsBody: String, timestamp: Instant): TransactionInfo? {
        // Extract the transaction amount (first amount in the message)
        val amountMatch = amountPattern.find(smsBody) ?: return null
        val amount = AmountParser.parseColombianAmount(amountMatch.groupValues[1]) ?: return null

        // Determine transaction type based on keywords
        val type = determineTransactionType(smsBody) ?: return null

        // Extract balance if present
        val balance = extractBalance(smsBody)

        // Extract merchant/recipient based on transaction type
        val (merchant, description) = extractMerchantAndDescription(smsBody, type)

        return TransactionInfo(
            type = type,
            amount = amount,
            balance = balance,
            merchant = merchant,
            description = description,
            bankName = bankName,
            timestamp = timestamp,
            rawMessage = smsBody,
        )
    }

    private fun determineTransactionType(smsBody: String): TransactionType? {
        return when {
            smsBody.contains("enviaste", ignoreCase = true) -> TransactionType.TRANSFER
            smsBody.contains("recibiste", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("pagaste", ignoreCase = true) -> TransactionType.DEBIT
            smsBody.contains("retiraste", ignoreCase = true) -> TransactionType.WITHDRAWAL
            smsBody.contains("recargaste", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("compra", ignoreCase = true) -> TransactionType.DEBIT
            debitKeywords.containsMatchIn(smsBody) -> TransactionType.DEBIT
            creditKeywords.containsMatchIn(smsBody) -> TransactionType.CREDIT
            else -> null
        }
    }

    private fun extractBalance(smsBody: String): Double? {
        val match = balancePattern.find(smsBody) ?: return null
        return AmountParser.parseColombianAmount(match.groupValues[1])
    }

    private fun extractMerchantAndDescription(
        smsBody: String,
        type: TransactionType,
    ): Pair<String?, String?> {
        return when (type) {
            TransactionType.TRANSFER -> {
                if (smsBody.contains("enviaste", ignoreCase = true)) {
                    val recipient = recipientPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                    Pair(recipient, "Transferencia enviada")
                } else {
                    Pair(null, "Transferencia")
                }
            }
            TransactionType.CREDIT -> {
                when {
                    smsBody.contains("recibiste", ignoreCase = true) -> {
                        val sender = senderPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                        Pair(sender, "Transferencia recibida")
                    }
                    smsBody.contains("recargaste", ignoreCase = true) -> {
                        Pair(null, "Recarga Nequi")
                    }
                    else -> Pair(null, "Crédito")
                }
            }
            TransactionType.DEBIT -> {
                val merchant = merchantPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                Pair(merchant, "Pago Nequi")
            }
            TransactionType.WITHDRAWAL -> {
                Pair(null, "Retiro Nequi")
            }
        }
    }

    private fun String.cleanField(): String? {
        return this
            .replace(Regex("""[.\s]+$"""), "")
            .replace(Regex("""^[.\s]+"""), "")
            .replace(Regex("""\s*Saldo.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() && it.length > 1 }
    }
}
