package com.example.moneytap.data.parser.banks

import com.example.moneytap.data.parser.AmountParser
import com.example.moneytap.data.parser.BankSmsParser
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.datetime.Instant

/**
 * Generic fallback parser for transaction SMS messages from unknown senders.
 *
 * This parser attempts to identify transactions by looking for common
 * Spanish expense-related keywords in the message body, regardless of sender.
 *
 * Used as a last resort when no specific bank parser matches the sender ID.
 */
class GenericTransactionParser : BankSmsParser {

    override val bankName: String = "Otro"

    // This parser doesn't match by sender - it's used as fallback
    override val senderIds: List<String> = emptyList()

    // Common Spanish keywords indicating an expense/transaction
    private val expenseKeywords = listOf(
        // Purchases
        "compra", "compraste", "compró",
        // Payments
        "pago", "pagaste", "pagó", "pagado",
        // Charges
        "cobro", "cobraste", "cobró", "cobrado",
        "cargo", "cargado",
        // Debits
        "débito", "debito", "debitado", "debitó",
        // Withdrawals
        "retiro", "retiraste", "retiró", "retirado",
        // Transactions
        "transacción", "transaccion", "transf",
        // Generic
        "gastaste", "gastó", "gasto",
    )

    // Keywords indicating income (to differentiate from expenses)
    private val incomeKeywords = listOf(
        "recibiste", "recibió", "recibido",
        "consignación", "consignacion", "consignado",
        "abono", "abonado", "abonó",
        "depósito", "deposito", "depositado",
        "ingreso", "ingresado",
        "te pagaron", "le pagaron",
    )

    // Keywords/patterns that indicate the message is a summary or informational, NOT a transaction
    private val exclusionKeywords = listOf(
        // Yearly/monthly summaries
        "suman tus gastos",
        "suman tus ingresos",
        "resumen del mes",
        "resumen mensual",
        "resumen anual",
        "balance del mes",
        "balance anual",
        "durante el 2024",
        "durante el 2025",
        "durante el 2026",
        "#tuinforme",
        "tus informes",
        "tu informe",
        // Marketing/promotional
        "aprovecha",
        "promoción",
        "promocion",
        "descuento especial",
        "nuevo beneficio",
        // Account alerts (not transactions)
        "tu clave dinámica",
        "tu clave dinamica",
        "cambio de clave",
        "actualiza tus datos",
        "encuesta",
        // OTP codes
        "código de verificación",
        "codigo de verificacion",
        "código otp",
        "codigo otp",
    )

    // Amount patterns - multiple formats
    private val amountPatterns = listOf(
        // $150.000 or $150,000 or $150.000,00
        Regex("""\$\s*([\d.,]+)"""),
        // COP 150.000 or COP150000
        Regex("""COP\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
        // 150.000 COP or 150,000 COP
        Regex("""([\d.,]+)\s*COP""", RegexOption.IGNORE_CASE),
        // por valor de $150.000 or por $150.000
        Regex("""por\s+(?:valor\s+de\s+)?\$?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
        // de $150.000
        Regex("""de\s+\$\s*([\d.,]+)"""),
    )

    // Merchant extraction patterns
    private val merchantPatterns = listOf(
        // "en MERCHANT" followed by space, period, or comma
        Regex("""\ben\s+([A-Z][A-Z0-9\s]{2,30}?)(?:\s+por|\s*[.,]|\s+el|\s+con|\$)""", RegexOption.IGNORE_CASE),
        // "establecimiento MERCHANT"
        Regex("""establecimiento\s+([A-Z][A-Z0-9\s]{2,30}?)(?:\s*[.,]|\s+por)""", RegexOption.IGNORE_CASE),
        // "comercio MERCHANT"
        Regex("""comercio\s+([A-Z][A-Z0-9\s]{2,30}?)(?:\s*[.,]|\s+por)""", RegexOption.IGNORE_CASE),
    )

    // Card number pattern
    private val cardPattern = Regex(
        """(?:tarjeta|tc|td|credencial|cuenta|\*)\s*\*?(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Checks if the message body contains transaction-related keywords.
     * This is used to determine if this parser should attempt to parse the message.
     *
     * Returns false for summary/informational messages that aren't actual transactions.
     */
    fun canParseBody(body: String): Boolean {
        val lowerBody = body.lowercase()

        // First check exclusions - these are NOT transactions
        val isExcluded = exclusionKeywords.any { lowerBody.contains(it) }
        if (isExcluded) {
            println("DEBUG: Message excluded by keyword filter: ${body.take(50)}...")
            return false
        }

        val hasExpenseKeyword = expenseKeywords.any { lowerBody.contains(it) }
        val hasIncomeKeyword = incomeKeywords.any { lowerBody.contains(it) }
        val hasAmount = amountPatterns.any { it.find(body) != null }

        return (hasExpenseKeyword || hasIncomeKeyword) && hasAmount
    }

    override fun canHandle(senderId: String): Boolean {
        // This parser doesn't match by sender ID - it's controlled by BankSmsParserFactory
        return false
    }

    override fun parse(smsBody: String, timestamp: Instant): TransactionInfo? {
        // Extract amount
        val amount = extractAmount(smsBody) ?: return null

        // Determine transaction type
        val type = determineTransactionType(smsBody)

        // Extract merchant
        val merchant = extractMerchant(smsBody)

        // Extract card number
        val cardLast4 = cardPattern.find(smsBody)?.groupValues?.get(1)
        println("DEBUG: ${smsBody} generic  ${amount}")

        return TransactionInfo(
            type = type,
            amount = amount,
            balance = null,
            cardLast4 = cardLast4,
            merchant = merchant,
            description = buildDescription(type, smsBody),
            reference = null,
            bankName = bankName,
            timestamp = timestamp,
            rawMessage = smsBody,
        )
    }

    private fun extractAmount(smsBody: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(smsBody)
            if (match != null) {
                val amountStr = match.groupValues[1]
                    .replace(Regex("""[A-Za-z]+$"""), "")
                    .trimEnd('.')
                val amount = AmountParser.parseColombianAmount(amountStr)
                if (amount != null && amount > 0) {
                    return amount
                }
            }
        }
        return null
    }

    private fun determineTransactionType(smsBody: String): TransactionType {
        val lowerBody = smsBody.lowercase()

        // Check for income first
        if (incomeKeywords.any { lowerBody.contains(it) }) {
            return TransactionType.CREDIT
        }

        // Check for withdrawals
        if (lowerBody.contains("retiro") || lowerBody.contains("retiraste") ||
            lowerBody.contains("cajero") || lowerBody.contains("atm")
        ) {
            return TransactionType.WITHDRAWAL
        }

        // Check for transfers
        if (lowerBody.contains("transferencia") || lowerBody.contains("transferiste") ||
            lowerBody.contains("transf")
        ) {
            return TransactionType.TRANSFER
        }

        // Default to DEBIT for purchases/payments
        return TransactionType.DEBIT
    }

    private fun extractMerchant(smsBody: String): String? {
        for (pattern in merchantPatterns) {
            val match = pattern.find(smsBody)
            if (match != null) {
                val merchant = match.groupValues[1]
                    .trim()
                    .replace(Regex("""\s+"""), " ")
                if (merchant.length >= 2) {
                    return merchant
                }
            }
        }
        return null
    }

    private fun buildDescription(type: TransactionType, smsBody: String): String {
        val lowerBody = smsBody.lowercase()
        return when {
            lowerBody.contains("qr") || lowerBody.contains("código qr") -> "Pago QR"
            lowerBody.contains("pse") -> "Pago PSE"
            lowerBody.contains("nequi") -> "Pago Nequi"
            lowerBody.contains("daviplata") -> "Pago Daviplata"
            type == TransactionType.WITHDRAWAL -> "Retiro"
            type == TransactionType.CREDIT -> "Ingreso"
            type == TransactionType.TRANSFER -> "Transferencia"
            else -> "Transacción"
        }
    }
}
