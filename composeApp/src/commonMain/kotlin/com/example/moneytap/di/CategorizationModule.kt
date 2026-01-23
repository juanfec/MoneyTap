package com.example.moneytap.di

import com.example.moneytap.domain.service.CategorizationEngine
import com.example.moneytap.domain.usecase.CategorizeTransactionsUseCase
import com.example.moneytap.domain.usecase.GetSpendingByCategoryUseCase
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val categorizationModule = module {
    singleOf(::CategorizationEngine)
    factoryOf(::CategorizeTransactionsUseCase)
    factoryOf(::GetSpendingByCategoryUseCase)
    factoryOf(::SpendingViewModel)
}
