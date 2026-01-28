package com.example.moneytap.di

import com.example.moneytap.data.database.DatabaseDriverFactory
import com.example.moneytap.data.datasource.local.PermissionHandler
import com.example.moneytap.data.datasource.local.SmsDataSource
import com.example.moneytap.data.repository.SmsRepositoryImpl
import com.example.moneytap.domain.repository.SmsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val smsAndroidModule = module {
    single { SmsDataSource(androidContext()) }
    single { PermissionHandler(androidContext()) }
    single<SmsRepository> { SmsRepositoryImpl(get()) }
    single { DatabaseDriverFactory(androidContext()) }
}
