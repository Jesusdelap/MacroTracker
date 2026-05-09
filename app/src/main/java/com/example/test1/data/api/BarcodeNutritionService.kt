package com.example.test1.data.api

class BarcodeNutritionService(
    private val fatSecret: FatSecretService = FatSecretService(),
    private val usda: UsdaService = UsdaService()
) {
    suspend fun lookup(barcode: String): Result<BarcodeResult> {
        val fsResult = fatSecret.lookup(barcode)
        if (fsResult.isSuccess) return fsResult
        return usda.lookup(barcode)
    }
}
