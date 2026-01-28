package com.example.moneytap.di

import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.data.repository.TransactionRepositoryImpl
import com.example.moneytap.domain.repository.TransactionRepository
import org.koin.dsl.module

val databaseModule = module {
    single {
        val driverFactory: com.example.moneytap.data.database.DatabaseDriverFactory = get()
        MoneyTapDatabase(driverFactory.createDriver())
    }
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }
}
