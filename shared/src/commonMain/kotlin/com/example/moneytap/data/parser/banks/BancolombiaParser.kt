package com.example.moneytap.data.parser.banks

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.data.parser.BankSmsParser
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Parser for Bancolombia transaction SMS messages.
 *
 * Example formats:
 * - "Compra por $150.000 con TC *1234 en ALMACEN EXITO. Consulte saldo: $500.000"
 * - "Retiro por $200.000 con TD *5678 en CAJERO AV CHILE"
 * - "Transferencia por $500.000 a cuenta 123456789"
 * - "Consignación por $750.000 en cuenta *9012"
 */
class BancolombiaParser : BankSmsParser {

    override val bankName: String = "Bancolombia"

    override val senderIds: List<String> = listOf(
        "Bancolombia",
        "85432",
    )

    // Colombian amount pattern: $150.000 or $1.500.000,50
    private val amountPattern = Regex("""\$\s*([\d.,]+)""")

    // Card/account number pattern: TC *1234 or TD *5678 or tarjeta *1234
    private val cardPattern = Regex(
        """(?:TC|TD|tarjeta|\*)\s*\*?(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    // Balance pattern
    private val balancePattern = Regex(
        """(?:saldo|disponible)[:\s]*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE,
    )

    // Merchant pattern: "en MERCHANT" followed by period or end
    private val merchantPattern = Regex("""\ben\s+([^.$]+)""", RegexOption.IGNORE_CASE)

    // Recipient pattern for transfers: "a cuenta/NAME"
    private val recipientPattern = Regex("""\ba\s+(?:cuenta\s+)?([^.$]+)""", RegexOption.IGNORE_CASE)

    // Reference pattern
    private val referencePattern = Regex(
        """(?:ref|referencia)[.:\s]*(\w+)""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(smsBody: String, timestamp: Instant): TransactionInfo? {
        // Extract the transaction amount (first amount in the message)
        val amountMatch = amountPattern.find(smsBody) ?: return null
        val amount = AmountParser.parseColombianAmount(amountMatch.groupValues[1]) ?: return null

        // Determine transaction type based on keywords
        val type = determineTransactionType(smsBody) ?: return null

        // Extract card number if present
        val cardLast4 = cardPattern.find(smsBody)?.groupValues?.get(1)

        // Extract balance if present
        val balance = extractBalance(smsBody)

        // Extract merchant/recipient based on transaction type
        val (merchant, description) = extractMerchantAndDescription(smsBody, type)

        // Extract reference if present
        val reference = referencePattern.find(smsBody)?.groupValues?.get(1)

        return TransactionInfo(
            type = type,
            amount = amount,
            balance = balance,
            cardLast4 = cardLast4,
            merchant = merchant,
            description = description,
            reference = reference,
            bankName = bankName,
            timestamp = timestamp,
            rawMessage = smsBody,
        )
    }

    private fun determineTransactionType(smsBody: String): TransactionType? {
        return when {
            smsBody.contains("compra", ignoreCase = true) -> TransactionType.DEBIT
            smsBody.contains("pago", ignoreCase = true) -> TransactionType.DEBIT
            smsBody.contains("retiro", ignoreCase = true) -> TransactionType.WITHDRAWAL
            smsBody.contains("transferencia", ignoreCase = true) -> {
                if (smsBody.contains("recibiste", ignoreCase = true) ||
                    smsBody.contains("le consignaron", ignoreCase = true)
                ) {
                    TransactionType.CREDIT
                } else {
                    TransactionType.TRANSFER
                }
            }
            smsBody.contains("consignación", ignoreCase = true) ||
                smsBody.contains("consignacion", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("abono", ignoreCase = true) -> TransactionType.CREDIT
            smsBody.contains("débito", ignoreCase = true) ||
                smsBody.contains("debito", ignoreCase = true) -> TransactionType.DEBIT
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
                val description = if (smsBody.contains("compra", ignoreCase = true)) {
                    "Compra"
                } else {
                    "Pago"
                }
                Pair(merchant, description)
            }
            TransactionType.WITHDRAWAL -> {
                val location = merchantPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                Pair(location, "Retiro")
            }
            TransactionType.TRANSFER -> {
                val recipient = recipientPattern.find(smsBody)?.groupValues?.get(1)?.cleanField()
                Pair(recipient, "Transferencia enviada")
            }
            TransactionType.CREDIT -> {
                val description = when {
                    smsBody.contains("consignación", ignoreCase = true) ||
                        smsBody.contains("consignacion", ignoreCase = true) -> "Consignación"
                    smsBody.contains("transferencia", ignoreCase = true) -> "Transferencia recibida"
                    else -> "Abono"
                }
                Pair(null, description)
            }
        }
    }

    private fun String.cleanField(): String? {
        return this
            .replace(Regex("""[.\s]+$"""), "")
            .replace(Regex("""^[.\s]+"""), "")
            .replace(Regex("""\s*(?:Consulte|Saldo).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() && it.length > 1 }
    }
}
