package com.example.moneytap.di

import com.example.moneytap.domain.service.CategorizationEngine
import com.example.moneytap.domain.service.CategoryTeachingEngine
import com.example.moneytap.domain.service.FuzzyPatternMatcher
import com.example.moneytap.domain.service.PatternInferenceEngine
import com.example.moneytap.domain.usecase.CategorizeTransactionsUseCase
import com.example.moneytap.domain.usecase.GetSpendingByCategoryUseCase
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import com.example.moneytap.presentation.viewmodel.TeachingViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val categorizationModule = module {
    // Existing bindings
    singleOf(::CategorizationEngine)
    factoryOf(::CategorizeTransactionsUseCase)
    factoryOf(::GetSpendingByCategoryUseCase)
    factoryOf(::SpendingViewModel)

    // New bindings for user-defined patterns (Phase 4-6)
    singleOf(::PatternInferenceEngine)
    single { FuzzyPatternMatcher() } // Use default parameters
    singleOf(::CategoryTeachingEngine)

    // ViewModel for teaching flow (Phase 8)
    factoryOf(::TeachingViewModel)
}
