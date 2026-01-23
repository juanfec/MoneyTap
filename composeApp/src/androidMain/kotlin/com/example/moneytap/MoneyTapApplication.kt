package com.example.moneytap

import android.app.Application
import com.example.moneytap.di.categorizationModule
import com.example.moneytap.di.smsAndroidModule
import com.example.moneytap.di.smsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MoneyTapApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MoneyTapApplication)
            modules(smsAndroidModule, smsModule, categorizationModule)
        }
    }
}
