package com.example.test1.data.api

import kotlinx.serialization.Serializable

@Serializable
data class IngredientMacro(
    val name: String,
    val cal: Int,
    val prot: Float,
    val carb: Float,
    val fat: Float
)

@Serializable
data class MacroResult(
    val name: String,
    val cal: Int,
    val prot: Float,
    val carb: Float,
    val fat: Float,
    val ingredients: List<IngredientMacro> = emptyList(),
    val fromRecipe: String? = null
)

@Serializable
data class FoodChatResponse(
    val name: String? = null,
    val cal: Int? = null,
    val prot: Float? = null,
    val carb: Float? = null,
    val fat: Float? = null,
    val question: String? = null,
    val error: String? = null,
    val ingredients: List<IngredientMacro>? = null,
    val fromRecipe: String? = null
) {
    val asMacroResult: MacroResult?
        get() = if (cal != null && prot != null && carb != null && fat != null)
            MacroResult(name ?: "", cal, prot, carb, fat, ingredients ?: emptyList(), fromRecipe)
        else null
}
