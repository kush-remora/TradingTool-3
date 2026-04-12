package com.tradingtool.core.delivery.model

enum class DeliveryUniverse(val storageValue: String) {
    LARGEMIDCAP_250("largemidcap250"),
    SMALLCAP_250("smallcap250"),
    WATCHLIST("watchlist");

    companion object {
        fun fromStorageValue(value: String): DeliveryUniverse {
            return entries.firstOrNull { universe -> universe.storageValue == value }
                ?: error("Unknown delivery universe value: $value")
        }
    }
}
