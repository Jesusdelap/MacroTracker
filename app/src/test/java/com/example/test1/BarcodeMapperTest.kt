package com.example.test1

import com.example.test1.data.api.BarcodeResult
import com.example.test1.data.api.toFoodItemEntity
import com.example.test1.data.db.entity.FoodItemSource
import com.example.test1.data.db.entity.FoodItemType
import com.example.test1.data.db.entity.ServingMode
import org.junit.Assert.*
import org.junit.Test

class BarcodeMapperTest {

    private val fatSecretResult = BarcodeResult(
        name           = "Chicken Breast",
        brand          = "Generic",
        barcode        = "1234567890123",
        kcalPer100g    = 165,
        proteinPer100g = 31.0f,
        carbsPer100g   = 0.0f,
        fatPer100g     = 3.6f,
        source         = FoodItemSource.FATSECRET,
        fatSecretId    = "54321"
    )

    private val usdaResult = BarcodeResult(
        name           = "WHOLE MILK",
        brand          = "Organic Valley",
        barcode        = "093966000046",
        kcalPer100g    = 61,
        proteinPer100g = 3.2f,
        carbsPer100g   = 4.8f,
        fatPer100g     = 3.3f,
        source         = FoodItemSource.USDA,
        usdaFdcId      = "167702"
    )

    @Test
    fun `FatSecret result maps to PRODUCT entity with PER_100G serving mode`() {
        val entity = fatSecretResult.toFoodItemEntity()
        assertEquals(FoodItemType.PRODUCT.name, entity.itemType)
        assertEquals(ServingMode.PER_100G.name, entity.servingMode)
        assertEquals(FoodItemSource.FATSECRET.name, entity.source)
        assertEquals("Chicken Breast", entity.name)
        assertEquals("Generic", entity.brand)
        assertEquals("1234567890123", entity.barcode)
        assertEquals(165, entity.kcalPerServing)
        assertEquals(31.0f, entity.protein)
        assertEquals(0.0f, entity.carbs)
        assertEquals(3.6f, entity.fat)
        assertEquals("54321", entity.fatSecretId)
        assertNull(entity.usdaFdcId)
    }

    @Test
    fun `USDA result maps to PRODUCT entity with correct source and id`() {
        val entity = usdaResult.toFoodItemEntity()
        assertEquals(FoodItemSource.USDA.name, entity.source)
        assertEquals(FoodItemType.PRODUCT.name, entity.itemType)
        assertEquals(ServingMode.PER_100G.name, entity.servingMode)
        assertEquals("167702", entity.usdaFdcId)
        assertNull(entity.fatSecretId)
        assertEquals(61, entity.kcalPerServing)
        assertEquals(3.2f, entity.protein)
    }

    @Test
    fun `mapper preserves zero macro values`() {
        val entity = fatSecretResult.copy(carbsPer100g = 0f, fatPer100g = 0f).toFoodItemEntity()
        assertEquals(0f, entity.carbs)
        assertEquals(0f, entity.fat)
    }

    @Test
    fun `mapper uses default values for non-product fields`() {
        val entity = fatSecretResult.toFoodItemEntity()
        assertEquals(0, entity.usageCount)
        assertFalse(entity.isFavorite)
        assertNull(entity.lastUsedAt)
        assertEquals(1f, entity.servings)
    }
}
