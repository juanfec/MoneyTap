package com.example.moneytap.di

import com.example.moneytap.domain.usecase.GetSmsMessagesUseCase
import com.example.moneytap.domain.usecase.ParseSmsTransactionsUseCase
import com.example.moneytap.presentation.viewmodel.SmsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val smsModule = module {
    factoryOf(::GetSmsMessagesUseCase)
    factoryOf(::ParseSmsTransactionsUseCase)
    factoryOf(::SmsViewModel)
}
