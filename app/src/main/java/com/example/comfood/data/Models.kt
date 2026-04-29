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
    val nutrition: NutritionEstimate? = null,
    val imageUrl: String? = null
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
    val matchedQueryScore: Int = 0,
    val imageUrl: String? = null
)

data class MealCandidate(
    val product: ProductInfo,
    val explanation: String,
    val confidence: Int,
    val mealComposition: MealComposition? = null
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
        val candidates: List<MealCandidate>,
        val composition: MealComposition? = null
    ) : MealLookupResult

    data class Failure(val message: String) : MealLookupResult
}

enum class FoodMatchType {
    Exact,
    Alias,
    Partial,
    Fuzzy,
    GenericFallback,
    Componentized,
    ExternalStructured
}

data class ResolvedIngredient(
    val id: String,
    val label: String,
    val quantityText: String?,
    val confidence: Int,
    val parentFoodId: String?,
    val estimatedCalories: Int? = null,
    val estimatedNutrition: NutritionEstimate? = null
)

data class ResolvedFoodItem(
    val id: String,
    val label: String,
    val brand: String?,
    val quantityText: String?,
    val quantityMultiplier: Double,
    val estimatedCalories: Int?,
    val estimatedNutrition: NutritionEstimate? = null,
    val confidence: Int,
    val matchType: FoodMatchType,
    val ingredients: List<ResolvedIngredient>
)

data class MealComposition(
    val originalInput: String,
    val foods: List<ResolvedFoodItem>,
    val ingredients: List<ResolvedIngredient>,
    val estimatedCalories: Int?,
    val overallConfidence: Int,
    val usedExternalLookup: Boolean
)

enum class FoodCatalogCategory {
    BaseFood,
    Ingredient,
    BrandedMeal
}

data class FoodCatalogEntry(
    val canonicalName: String,
    val aliases: List<String>,
    val category: FoodCatalogCategory,
    val typicalServingUnit: String,
    val caloriesPerUnit: Double,
    val nutritionPerUnit: NutritionEstimate? = null,
    val sourceBrand: String? = null
)
