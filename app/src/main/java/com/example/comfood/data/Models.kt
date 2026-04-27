package com.example.comfood.data

data class MacroEstimate(
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double
)

data class NutritionEstimate(
    val fiberGrams: Double = 0.0,
    val sugarGrams: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val ironMg: Double = 0.0,
    val vitaminCMg: Double = 0.0,
    val vitaminDMcg: Double = 0.0,
    val vitaminAMcg: Double = 0.0,
    val vitaminB12Mcg: Double = 0.0
)

data class FoodLogEntry(
    val id: String,
    val title: String,
    val source: String,
    val timestampUtcMillis: Long,
    val macros: MacroEstimate,
    val nutrition: NutritionEstimate? = null
)

data class PendingMealApproval(
    val id: String,
    val originalQuery: String,
    val sourceDevice: String,
    val timestampUtcMillis: Long,
    val candidates: List<MealCandidate>
)

data class IngredientRule(
    val key: String,
    val label: String,
    val reason: String,
    val aliases: List<String>,
    val section: IngredientSection,
    val isCommon: Boolean = false
)

data class BrandProfile(
    val canonical: String,
    val aliases: List<String>,
    val menuHints: List<String>
)

data class LocalMenuItem(
    val restaurant: String,
    val name: String,
    val aliases: List<String>,
    val macros: MacroEstimate
)

enum class IngredientSection {
    Common,
    Additives,
    Sweeteners,
    Oils,
    Preservatives,
    Dyes,
    Allergens
}

data class ProductInfo(
    val barcode: String,
    val name: String,
    val brand: String?,
    val ingredientsText: String?,
    val sourceUrl: String,
    val macrosPer100g: MacroEstimate?,
    val nutritionPerServing: NutritionEstimate? = null,
    val matchedQueryScore: Int = 0
)

data class MealCandidate(
    val product: ProductInfo,
    val explanation: String,
    val confidence: Int
)

data class ProductReview(
    val product: ProductInfo,
    val blockedIngredients: List<IngredientRule>
)

sealed interface LocalMenuMatchResult {
    val parsed: ParsedMealQuery

    data class Exact(
        override val parsed: ParsedMealQuery,
        val item: LocalMenuItem
    ) : LocalMenuMatchResult

    data class Ambiguous(
        override val parsed: ParsedMealQuery,
        val candidates: List<LocalMenuItem>
    ) : LocalMenuMatchResult

    data class NoMatch(
        override val parsed: ParsedMealQuery
    ) : LocalMenuMatchResult
}

sealed interface MealLookupResult {
    data class Success(
        val candidates: List<MealCandidate>
    ) : MealLookupResult

    data class Failure(val message: String) : MealLookupResult
}
