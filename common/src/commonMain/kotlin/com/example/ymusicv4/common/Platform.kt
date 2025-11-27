package com.example.ymusicv4.common

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
