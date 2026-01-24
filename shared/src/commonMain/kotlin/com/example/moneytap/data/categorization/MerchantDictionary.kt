package com.example.moneytap.data.categorization

import com.example.moneytap.domain.model.Category

/**
 * Dictionary of known merchants and their categories.
 * Focused on Colombian merchants commonly found in bank SMS messages.
 */
object MerchantDictionary {

    /**
     * Map of exact merchant names (uppercase) to their categories.
     * Used for exact matching in the first layer of categorization.
     */
    val merchantToCategory: Map<String, Category> = buildMap {
        // Groceries - Colombian supermarkets
        listOf(
            "EXITO", "ALMACENES EXITO", "CARULLA", "JUMBO", "D1", "TIENDAS D1",
            "ARA", "TIENDAS ARA", "OLIMPICA", "SUPERTIENDAS OLIMPICA",
            "ALKOSTO", "MAKRO", "HOMECENTER", "EURO", "SURTIMAX",
            "LA 14", "COLSUBSIDIO", "COORATIENDAS", "MERQUEO", "SUPERM MAS POR MENOS",
        ).forEach { put(it, Category.GROCERIES) }

        // Restaurants & Food Delivery
        listOf(
            "RAPPI", "RAPPI COLOMBIA", "IFOOD", "UBER EATS", "DOMICILIOS COM",
            "MCDONALDS", "MC DONALDS", "BURGER KING", "SUBWAY",
            "CREPES", "CREPES Y WAFFLES", "EL CORRAL", "PRESTO", "KOKORIKO",
            "DOMINOS", "DOMINOS PIZZA", "PIZZA HUT", "JENO'S PIZZA",
            "FRISBY", "PPC", "ANDRÉS CARNE DE RES", "ANDRES DC",
            "WOK", "ARCHIES", "TACOS Y BAR BQ",
        ).forEach { put(it, Category.RESTAURANT) }

        // Coffee Shops
        listOf(
            "JUAN VALDEZ", "JUAN VALDEZ CAFE", "STARBUCKS", "TOSTAO", "TOSTAO CAFE",
            "OMA", "OMA CAFE", "DUNKIN", "DUNKIN DONUTS",
        ).forEach { put(it, Category.COFFEE) }

        // Rideshare & Taxi
        listOf(
            "UBER", "UBER TRIP", "UBER BV", "DIDI", "DIDI CHUXING",
            "CABIFY", "BEAT", "BEAT RIDE", "INDRIVER", "PICAP",
        ).forEach { put(it, Category.TAXI_RIDESHARE) }

        // Gas Stations
        listOf(
            "TERPEL", "ESTACION TERPEL", "PRIMAX", "MOBIL", "TEXACO",
            "ESSO", "BIOMAX", "PETROBRAS", "BRIO", "ZEUSS",
        ).forEach { put(it, Category.GAS) }

        // Public Transit
        listOf(
            "TRANSMILENIO", "SITP", "TU LLAVE", "TULLAVE", "METRO MEDELLIN",
            "MIO CALI", "METROLINEA", "TRANSMETRO",
        ).forEach { put(it, Category.TRANSMILENIO) }

        // Utilities
        listOf(
            "EPM", "CODENSA", "ENEL", "ETB", "VANTI", "GAS NATURAL",
            "ACUEDUCTO", "EAAB", "CLARO", "MOVISTAR", "TIGO", "WOM",
            "DIRECTV", "HBO", "NETFLIX", "SPOTIFY",
        ).forEach { put(it, Category.UTILITIES) }

        // Health & Medical
        listOf(
            "EPS SURA", "EPS SANITAS", "NUEVA EPS", "SALUD TOTAL",
            "COMPENSAR", "FAMISANAR", "COOMEVA EPS", "MEDIMAS",
        ).forEach { put(it, Category.EPS_HEALTH) }

        // Pharmacies
        listOf(
            "DROGUERIA", "CRUZ VERDE", "LA REBAJA", "FARMATODO",
            "DROGAS LA ECONOMIA", "LOCATEL", "AUDIFARMA",
        ).forEach { put(it, Category.PHARMACY) }

        // Bank Fees
        listOf(
            "4X1000", "4XMIL", "CUATRO POR MIL", "GMF", "IVA",
        ).forEach { put(it, Category.CUATRO_X_MIL) }

        // Property/Building Admin
        listOf(
            "ADMINISTRACION", "ADMIN EDIFICIO", "CONJUNTO", "PROPIEDAD HORIZONTAL",
        ).forEach { put(it, Category.ADMINISTRACION) }
    }

    /**
     * Map of keywords to categories.
     * Used for keyword matching when exact/fuzzy matching fails.
     */
    val keywordToCategory: Map<String, Category> = mapOf(
        // Groceries keywords
        "SUPERMERCADO" to Category.GROCERIES,
        "SUPERMARKET" to Category.GROCERIES,
        "TIENDA" to Category.GROCERIES,
        "MERCADO" to Category.GROCERIES,
        "MINIMARKET" to Category.GROCERIES,
        "MINIMERCADO" to Category.GROCERIES,
        "FRUVER" to Category.GROCERIES,

        // Restaurant keywords
        "RESTAURANTE" to Category.RESTAURANT,
        "RESTAURANT" to Category.RESTAURANT,
        "PANADERIA" to Category.RESTAURANT,
        "PIZZERIA" to Category.RESTAURANT,
        "COMIDAS" to Category.RESTAURANT,
        "ASADERO" to Category.RESTAURANT,
        "COMIDA RAPIDA" to Category.RESTAURANT,

        // Coffee keywords
        "CAFE" to Category.COFFEE,
        "COFFEE" to Category.COFFEE,
        "CAFETERIA" to Category.COFFEE,

        // Transport keywords
        "GASOLINA" to Category.GAS,
        "COMBUSTIBLE" to Category.GAS,
        "ESTACION" to Category.GAS,
        "PEAJE" to Category.TRANSMILENIO,
        "PARQUEADERO" to Category.UNCATEGORIZED,
        "PARKING" to Category.UNCATEGORIZED,

        // Rideshare keywords
        "TAXI" to Category.TAXI_RIDESHARE,
        "VIAJE" to Category.TAXI_RIDESHARE,

        // Health keywords
        "DROGUERIA" to Category.PHARMACY,
        "FARMACIA" to Category.PHARMACY,
        "CLINICA" to Category.EPS_HEALTH,
        "HOSPITAL" to Category.EPS_HEALTH,
        "MEDICO" to Category.EPS_HEALTH,
        "SALUD" to Category.EPS_HEALTH,

        // Utilities keywords
        "SERVICIOS" to Category.UTILITIES,
        "RECARGA" to Category.UTILITIES,
        "CELULAR" to Category.UTILITIES,

        // Bank fees keywords
        "IMPUESTO" to Category.CUATRO_X_MIL,
        "GMF" to Category.CUATRO_X_MIL,
    )

    /**
     * Returns all known merchant names for iteration during fuzzy matching.
     */
    fun getAllMerchantNames(): Set<String> = merchantToCategory.keys
}
