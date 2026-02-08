package com.example.moneytap.domain.model

/**
 * Primary spending categories for high-level grouping.
 */
enum class PrimaryCategory(val displayName: String) {
    FOOD_AND_DRINK("Food & Drink"),
    TRANSPORTATION("Transportation"),
    RENT_AND_UTILITIES("Rent & Utilities"),
    BANK_FEES("Bank Fees"),
    MEDICAL("Medical"),
    GENERAL_MERCHANDISE("General"),
    INTERNAL_TRANSFERS("Transfers"),
}

/**
 * Detailed spending categories for transaction classification.
 * Each category belongs to a [PrimaryCategory] for hierarchical grouping.
 */
enum class Category(
    val primaryCategory: PrimaryCategory,
    val displayName: String,
    val excludeFromSpending: Boolean = false,
) {
    GROCERIES(PrimaryCategory.FOOD_AND_DRINK, "Groceries"),
    RESTAURANT(PrimaryCategory.FOOD_AND_DRINK, "Restaurant"),
    COFFEE(PrimaryCategory.FOOD_AND_DRINK, "Coffee"),
    GAS(PrimaryCategory.TRANSPORTATION, "Gas"),
    TAXI_RIDESHARE(PrimaryCategory.TRANSPORTATION, "Taxi/Rideshare"),
    TRANSMILENIO(PrimaryCategory.TRANSPORTATION, "TransMilenio"),
    ADMINISTRACION(PrimaryCategory.RENT_AND_UTILITIES, "Administración"),
    UTILITIES(PrimaryCategory.RENT_AND_UTILITIES, "Utilities"),
    CUATRO_X_MIL(PrimaryCategory.BANK_FEES, "4x1000"),
    EPS_HEALTH(PrimaryCategory.MEDICAL, "EPS/Health"),
    PHARMACY(PrimaryCategory.MEDICAL, "Pharmacy"),
    CREDIT_CARD_PAYMENT(PrimaryCategory.INTERNAL_TRANSFERS, "Credit Card Payment", excludeFromSpending = true),
    UNCATEGORIZED(PrimaryCategory.GENERAL_MERCHANDISE, "Uncategorized"),
}
