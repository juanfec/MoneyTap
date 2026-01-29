package com.example.moneytap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AmountFormat(
    val thousandsSeparator: Char,
    val decimalSeparator: Char,
    val currencySymbol: String?,
    val currencyPosition: CurrencyPosition,
)

@Serializable
enum class CurrencyPosition { BEFORE, AFTER, NONE }
