package com.example.moneytap.data.parser.banks

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.data.parser.BankSmsParser
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Parser for Daviplata digital wallet transaction SMS messages.
 *
 * Example formats:
 * - "Pagaste $25.000 en RAPPI. Saldo disponible: $175.000"
 * - "Enviaste $50.000 a 3001234567. Saldo: $150.000"
 * - "Te enviaron $75.000 desde 3109876543. Saldo: $225.000"
 * - "Retiraste $120.000. Saldo: $80.000"
 * - "Recargaste $200.000. Saldo: $280.000"
 */
class DaviplataParser : BankSmsParser {

    override val bankName: String = "Daviplata"

    override val senderIds: List<String> = listOf(
        "Daviplata",
        "Davivienda",
        "85594",
    )

    // Colombian amount pattern: $25.000 or $1.500.000,50
    private val amountPattern = Regex("""\$\s*([\d.,]+)""")

    // Balance pattern
    private val balancePattern = Regex(
        """(?:saldo|disponible)[:\s]*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE,
    )

    // Merchant pattern: "en MERCHANT" followed by period or end
    private val merchantPattern = Regex("""\ben\s+([^.$]+)""", RegexOption.IGNORE_CASE)

    // Recipient pattern: "a PHONE/NAME" (for transfers)
    private val recipientPattern = Regex("""\ba\s+(\d{10}|[^.$]+)""", RegexOption.IGNORE_CASE)

    // Sender pattern: "desde/de PHONE/NAME" (for received transfers)
    private val senderPattern = Regex("""(?:desde|de)\s+(\d{10}|[^.$]+)""", RegexOption.IGNORE_CASE)

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
            smsBody.contains("pagaste", ignoreCase = true) -> TransactionType.DEBIT
            smsBody.contains("compra", ignoreCase = true) -> TransactionType.DEBIT
            smsBody.contains("enviaste", ignoreCase = true) -> TransactionType.TRANSFER
            smsBody.contains("transferiste", ignoreCase = true) -> TransactionType.TRANSFER
            smsBody.contains("te enviaron", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("recibiste", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("retiraste", ignoreCase = true) -> TransactionType.WITHDRAWAL
            smsBody.contains("recargaste", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("recarga", ignoreCase = true) -> TransactionType.CREDIT
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
            TransactionType.DEBIT -> {
                val merchant = merchantPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                Pair(merchant, "Pago Daviplata")
            }
            TransactionType.TRANSFER -> {
                val recipient = recipientPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                val maskedRecipient = recipient?.maskPhoneNumber()
                val description = if (smsBody.contains("transferiste", ignoreCase = true)) {
                    "Transferencia de saldo"
                } else {
                    "Transferencia enviada"
                }
                Pair(maskedRecipient, description)
            }
            TransactionType.CREDIT -> {
                when {
                    smsBody.contains("te enviaron", ignoreCase = true) ||
                        smsBody.contains("recibiste", ignoreCase = true) -> {
                        val sender = senderPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                        Pair(sender?.maskPhoneNumber(), "Transferencia recibida")
                    }
                    smsBody.contains("recargaste", ignoreCase = true) ||
                        smsBody.contains("recarga", ignoreCase = true) -> {
                        Pair(null, "Recarga Daviplata")
                    }
                    else -> Pair(null, "Crédito")
                }
            }
            TransactionType.WITHDRAWAL -> {
                Pair(null, "Retiro Daviplata")
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

    private fun String.maskPhoneNumber(): String {
        return if (this.matches(Regex("""\d{10}"""))) {
            "${this.take(3)}****${this.takeLast(3)}"
        } else {
            this
        }
    }
}
