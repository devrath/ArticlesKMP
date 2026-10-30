package com.istudio.articleskmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform