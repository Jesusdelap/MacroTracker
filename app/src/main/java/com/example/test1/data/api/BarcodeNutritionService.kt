package com.example.test1.data.api

class BarcodeNutritionService(
    private val fatSecret: FatSecretService = FatSecretService(),
    private val usda: UsdaService = UsdaService()
) {
    suspend fun lookup(barcode: String): Result<BarcodeResult> {
        val fsResult = fatSecret.lookup(barcode)
        if (fsResult.isSuccess) return fsResult
        val usdaResult = usda.lookup(barcode)
        if (usdaResult.isSuccess) return usdaResult

        val errors = listOfNotNull(fsResult.exceptionOrNull(), usdaResult.exceptionOrNull())
        return Result.failure(mergedLookupError(errors, barcode))
    }

    private fun mergedLookupError(errors: List<Throwable>, barcode: String): BarcodeLookupException {
        val reasons = errors.map { (it as? BarcodeLookupException)?.reason ?: BarcodeLookupError.Unknown }
        val reason = when {
            reasons.any { it == BarcodeLookupError.Network } -> BarcodeLookupError.Network
            reasons.all { it == BarcodeLookupError.NotFound } -> BarcodeLookupError.NotFound
            reasons.any { it == BarcodeLookupError.Configuration } -> BarcodeLookupError.Configuration
            reasons.any { it == BarcodeLookupError.ServiceUnavailable } -> BarcodeLookupError.ServiceUnavailable
            else -> BarcodeLookupError.Unknown
        }
        return BarcodeLookupException(
            reason = reason,
            message = "Barcode $barcode lookup failed: ${errors.joinToString { it.message.orEmpty() }}"
        )
    }
}
