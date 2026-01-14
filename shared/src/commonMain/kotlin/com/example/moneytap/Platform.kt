package com.example.moneytap

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform