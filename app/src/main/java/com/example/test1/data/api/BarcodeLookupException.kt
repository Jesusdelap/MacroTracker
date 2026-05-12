package com.example.test1.data.api

enum class BarcodeLookupError {
    NotFound,
    Network,
    ServiceUnavailable,
    Configuration,
    Unknown
}

class BarcodeLookupException(
    val reason: BarcodeLookupError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
