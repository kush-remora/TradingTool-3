package com.tradingtool.core.kite

data class KiteConfig(
    val apiKey: String,
    val apiSecret: String,
    // Short-lived token set via env or manual login. Empty = not yet authenticated.
    val accessToken: String,
)
