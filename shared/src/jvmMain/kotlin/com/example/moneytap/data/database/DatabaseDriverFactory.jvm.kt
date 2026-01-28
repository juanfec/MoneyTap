package com.example.moneytap.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".moneytap")
        dbDir.mkdirs()
        val dbFile = File(dbDir, "moneytap.db")
        val dbExists = dbFile.exists()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (!dbExists) {
            MoneyTapDatabase.Schema.create(driver)
        }
        return driver
    }
}
