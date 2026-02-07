package com.example.moneytap.di

import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.data.repository.TransactionRepositoryImpl
import com.example.moneytap.data.repository.UserPatternRepositoryImpl
import com.example.moneytap.data.repository.UserRuleRepositoryImpl
import com.example.moneytap.domain.repository.TransactionRepository
import com.example.moneytap.domain.repository.UserPatternRepository
import com.example.moneytap.domain.repository.UserRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

val databaseModule = module {
    single {
        val driverFactory: com.example.moneytap.data.database.DatabaseDriverFactory = get()
        MoneyTapDatabase(driverFactory.createDriver())
    }

    // Existing bindings
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }

    // New bindings for user-defined patterns (Phase 3)
    single<UserPatternRepository> { UserPatternRepositoryImpl(get(), Dispatchers.IO) }
    single<UserRuleRepository> { UserRuleRepositoryImpl(get(), Dispatchers.IO) }
}
