package com.example.comfood.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class OpenFoodFactsService(
    private val brandProfiles: List<BrandProfile>,
    private val localMenuItems: List<LocalMenuItem>,
    private val ingredientRules: List<IngredientRule> = emptyList(),
    private val usdaService: UsdaFoodDataCentralService? = null
) {
    private val parser = RuleBasedMealParser(brandProfiles)
    private val engine = FoodResolutionEngine(localMenuItems, ingredientRules)

    suspend fun estimateMeal(query: String): MealLookupResult {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return MealLookupResult.Failure("Add a meal description to estimate macros.")
        }

        val offline = engine.resolveOffline(cleaned)
        val parsed = parser.parse(cleaned)
        val fallbackQueries = buildFallbackQueries(cleaned, parsed, offline.unresolvedSegments)
        val externalCandidates = mutableListOf<MealCandidate>()

        runCatching {
            externalCandidates += structuredOpenFoodFactsCandidates(
                originalQuery = cleaned,
                fallbackQueries = fallbackQueries,
                baseComposition = offline.composition
            )
        }

        runCatching {
            val usdaProducts = usdaService?.searchFoods(fallbackQueries).orEmpty()
            externalCandidates += usdaProducts.mapNotNull { product ->
                val score = product.matchStructured(cleaned.normalizeFoodText())
                if (score >= 40) { // Lowered threshold to see more options alongside on-device
                    MealCandidate(
                        product = product.copy(matchedQueryScore = score),
                        explanation = "USDA Reference Match",
                        confidence = score,
                        mealComposition = offline.composition.copy(usedExternalLookup = true)
                    )
                } else {
                    null
                }
            }
        }

        // Tag offline candidates more clearly
        val labeledOffline = offline.offlineCandidates.map { 
            it.copy(explanation = "On-Device Guess: ${it.explanation}")
        }

        val merged = (labeledOffline + externalCandidates)
            .filter { it.product.macrosPer100g != null }
            .sortedByDescending { it.confidence }
            .distinctBy { 
                val key = "${it.product.name.normalizeFoodText()}|${it.product.brand.orEmpty().normalizeFoodText()}"
                when {
                    it.product.barcode.isNotBlank() -> it.product.barcode
                    else -> "${it.explanation}|$key"
                }
            }
            .take(30)

        if (merged.isNotEmpty()) {
            val usedExternal = merged.any {
                it.explanation.contains("Open Food Facts", ignoreCase = true) ||
                    it.explanation.contains("USDA", ignoreCase = true)
            }
            val composition = offline.composition.copy(usedExternalLookup = usedExternal)
            return MealLookupResult.Success(
                candidates = merged.map { candidate ->
                    if (candidate.mealComposition == null) {
                        candidate.copy(mealComposition = composition)
                    } else {
                        candidate
                    }
                },
                composition = composition
            )
        }

        return if (offline.offlineCandidates.isNotEmpty()) {
            MealLookupResult.Success(
                candidates = offline.offlineCandidates,
                composition = offline.composition
            )
        } else {
            MealLookupResult.Failure("I couldn't find a reliable structured meal interpretation yet.")
        }
    }

    suspend fun lookupBarcode(barcode: String): Result<ProductInfo> = runCatching {
        val cleaned = barcode.filter(Char::isDigit)
        require(cleaned.isNotEmpty()) { "Enter a valid barcode first." }
        val url = "https://world.openfoodfacts.org/api/v2/product/$cleaned.json"
        val body = fetch(url)
        val root = JSONObject(body)
        if (root.optInt("status") != 1) {
            error("No product was found for that barcode.")
        }
        val product = root.getJSONObject("product")
        parseProduct(product, cleaned)
    }

    private fun buildFallbackQueries(
        original: String,
        parsed: ParsedMealQuery,
        unresolvedSegments: List<String>
    ): List<String> {
        val variants = linkedSetOf<String>()
        variants += unresolvedSegments.filter { it.isNotBlank() }
        variants += original.trim()
        variants += parsed.normalized
        parsed.brand?.let { brand ->
            variants += brand.canonical
            val withoutBrand = parsed.normalized.replace(brand.canonical, "").trim()
            if (withoutBrand.isNotBlank()) {
                variants += "$withoutBrand ${brand.canonical}".trim()
                variants += withoutBrand
            }
        }
        return variants.filter { it.isNotBlank() }.take(5)
    }

    private fun structuredOpenFoodFactsCandidates(
        originalQuery: String,
        fallbackQueries: List<String>,
        baseComposition: MealComposition
    ): List<MealCandidate> {
        val normalizedOriginal = originalQuery.normalizeFoodText()
        val products = fallbackQueries
            .flatMap { phrase -> 
                val results = fetchProducts(searchUrlFor(phrase))
                if (results.isEmpty() && phrase.contains(" ")) {
                    // Try searching each word if the phrase failed
                    phrase.split(" ").flatMap { fetchProducts(searchUrlFor(it)) }
                } else results
            }
            .distinctBy { it.barcode.ifBlank { "${it.name}|${it.brand}" } }

        return products.mapNotNull { product ->
            val score = product.matchStructured(normalizedOriginal)
            if (score < 40) return@mapNotNull null

            MealCandidate(
                product = product.copy(matchedQueryScore = score),
                explanation = "Open Food Facts Match",
                confidence = score,
                mealComposition = baseComposition.copy(usedExternalLookup = true)
            )
        }
    }

    private fun searchUrlFor(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=100&fields=code,product_name,product_name_en,brands,ingredients_text,nutriments,image_url,image_front_url"
    }

    private fun fetchProducts(url: String): List<ProductInfo> {
        val body = fetch(url)
        val root = JSONObject(body)
        val products = root.optJSONArray("products") ?: JSONArray()
        return buildList {
            for (index in 0 until products.length()) {
                val product = products.optJSONObject(index) ?: continue
                val barcode = product.optString("code")
                add(parseProduct(product, barcode))
            }
        }.filter { it.macrosPer100g != null }
    }

    private fun parseProduct(product: JSONObject, barcode: String): ProductInfo {
        val nutriments = product.optJSONObject("nutriments") ?: JSONObject()
        
        // Robust calorie detection
        val calories = sequenceOf(
            "energy-kcal_serving",
            "energy-kcal_100g",
            "energy-kcal",
            "energy_serving",
            "energy_100g"
        ).map { key -> nutriments.optDouble(key) }
         .filter { !it.isNaN() && it > 0 }
         .firstOrNull()
        
        val isKj = calories != null && (nutriments.has("energy_100g") || nutriments.has("energy_serving")) && !nutriments.has("energy-kcal_100g")
        val finalCalories = if (isKj) (calories / 4.184) else calories
        
        val isPerServing = nutriments.has("energy-kcal_serving") || nutriments.has("energy_serving")
        
        val protein = if (isPerServing) nutriments.optDouble("proteins_serving") else nutriments.optDouble("proteins_100g")
        val carbs = if (isPerServing) nutriments.optDouble("carbohydrates_serving") else nutriments.optDouble("carbohydrates_100g")
        val fat = if (isPerServing) nutriments.optDouble("fat_serving") else nutriments.optDouble("fat_100g")
        
        val proteinGrams = if (protein.isNaN()) 0.0 else protein
        val carbsGrams = if (carbs.isNaN()) 0.0 else carbs
        val fatGrams = if (fat.isNaN()) 0.0 else fat
        
        val macroCalories = finalCalories?.roundToInt()
        
        val macros = if (macroCalories != null) {
            MacroEstimate(
                calories = macroCalories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams
            )
        } else null
        val nutrition = nutriments.let {
            NutritionEstimate(
                fiberGrams = it.optDouble("fiber_100g").takeUnless(Double::isNaN) ?: 0.0,
                sugarGrams = it.optDouble("sugars_100g").takeUnless(Double::isNaN) ?: 0.0,
                sodiumMg = ((it.optDouble("sodium_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0),
                potassiumMg = ((it.optDouble("potassium_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0),
                calciumMg = ((it.optDouble("calcium_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0),
                ironMg = (it.optDouble("iron_100g").takeUnless(Double::isNaN) ?: 0.0),
                vitaminCMg = (it.optDouble("vitamin-c_100g").takeUnless(Double::isNaN) ?: 0.0),
                vitaminDMcg = ((it.optDouble("vitamin-d_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0),
                vitaminAMcg = ((it.optDouble("vitamin-a_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0),
                vitaminB12Mcg = ((it.optDouble("vitamin-b12_100g").takeUnless(Double::isNaN) ?: 0.0) * 1000.0)
            )
        }

        val sourceUrl = when {
            barcode.isNotBlank() -> "https://world.openfoodfacts.org/product/$barcode"
            else -> "https://world.openfoodfacts.org/"
        }

        val imageUrl = product.optString("image_url")
            .ifBlank { product.optString("image_front_url") }
            .ifBlank { null }

        return ProductInfo(
            barcode = barcode,
            name = product.optString("product_name_en")
                .ifBlank { product.optString("product_name") }
                .ifBlank { "Unnamed product" },
            brand = product.optString("brands").ifBlank { null },
            ingredientsText = product.optString("ingredients_text").ifBlank { null },
            sourceUrl = sourceUrl,
            macrosPer100g = macros,
            nutritionPerServing = nutrition,
            imageUrl = imageUrl
        )
    }

    private fun ProductInfo.matchStructured(normalizedQuery: String): Int {
        val normalizedName = name.normalizeFoodText()
        val normalizedBrand = brand.orEmpty().normalizeFoodText()
        val queryTokens = normalizedQuery.tokens()
        if (queryTokens.isEmpty()) return 15
        
        val nameTokens = normalizedName.tokens()
        val brandTokens = normalizedBrand.tokens()

        val overlapCount = queryTokens.count { it in nameTokens || it in brandTokens }
        val coverage = overlapCount.toDouble() / queryTokens.size
        
        val exactName = normalizedName == normalizedQuery
        val containsName = normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)
        
        var score = (coverage * 80).toInt()
        if (exactName) score += 20
        else if (containsName) score += 10
        
        // Bonus for having macros
        if (macrosPer100g != null) score += 5
        
        return score.coerceIn(0, 100)
    }

    private fun String.tokens(): Set<String> =
        normalizeFoodText()
            .split(" ")
            .filter { it.length > 2 }
            .toSet()

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty(
                "User-Agent",
                "ComFood - Android - Version 1.0 - Hackathon Demo"
            )
            setRequestProperty("Accept", "application/json")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
    }
}
