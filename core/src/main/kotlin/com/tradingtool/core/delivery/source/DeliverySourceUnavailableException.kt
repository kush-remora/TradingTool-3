package com.tradingtool.core.delivery.source

class DeliverySourceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

