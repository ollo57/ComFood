package com.example.comfood.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
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
        if (!offline.needsExternalFallback && offline.offlineCandidates.isNotEmpty()) {
            return MealLookupResult.Success(
                candidates = offline.offlineCandidates,
                composition = offline.composition
            )
        }

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
                if (score >= EXTERNAL_ACCEPT_THRESHOLD) {
                    MealCandidate(
                        product = product.copy(matchedQueryScore = score),
                        explanation = "Structured USDA fallback match.",
                        confidence = score,
                        mealComposition = offline.composition.copy(usedExternalLookup = true)
                    )
                } else {
                    null
                }
            }
        }

        val merged = (
            offline.offlineCandidates +
                externalCandidates
            )
            .filter { it.product.macrosPer100g != null }
            .sortedByDescending { it.confidence }
            .distinctBy { "${it.product.name.normalizeFoodText()}|${it.product.brand.orEmpty().normalizeFoodText()}" }
            .take(6)

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
            .flatMap { phrase -> fetchProducts(searchUrlFor(phrase)) }
            .distinctBy { "${it.barcode}-${it.name.normalizeFoodText()}-${it.brand.orEmpty().normalizeFoodText()}" }

        return products.mapNotNull { product ->
            val score = product.matchStructured(normalizedOriginal)
            if (score < EXTERNAL_ACCEPT_THRESHOLD) return@mapNotNull null

            MealCandidate(
                product = product.copy(matchedQueryScore = score),
                explanation = "Structured Open Food Facts fallback match.",
                confidence = score,
                mealComposition = baseComposition.copy(usedExternalLookup = true)
            )
        }
    }

    private fun searchUrlFor(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return "https://world.openfoodfacts.org/api/v2/search?search_terms=$encoded&search_simple=1&json=1&page_size=24&nocache=1&fields=code,product_name,product_name_en,brands,ingredients_text,nutriments"
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
        val nutriments = product.optJSONObject("nutriments")
        val calories =
            nutriments?.optDouble("energy-kcal_100g")?.takeUnless { it.isNaN() }?.roundToInt()
        val protein = nutriments?.optDouble("proteins_100g")?.takeUnless { it.isNaN() }
        val carbs = nutriments?.optDouble("carbohydrates_100g")?.takeUnless { it.isNaN() }
        val fat = nutriments?.optDouble("fat_100g")?.takeUnless { it.isNaN() }
        val macros =
            if (calories != null && protein != null && carbs != null && fat != null) {
                MacroEstimate(
                    calories = calories,
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat
                )
            } else {
                null
            }
        val nutrition = nutriments?.let {
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

        return ProductInfo(
            barcode = barcode,
            name = product.optString("product_name_en")
                .ifBlank { product.optString("product_name") }
                .ifBlank { "Unnamed product" },
            brand = product.optString("brands").ifBlank { null },
            ingredientsText = product.optString("ingredients_text").ifBlank { null },
            sourceUrl = sourceUrl,
            macrosPer100g = macros,
            nutritionPerServing = nutrition
        )
    }

    private fun ProductInfo.matchStructured(normalizedQuery: String): Int {
        val normalizedName = name.normalizeFoodText()
        val normalizedBrand = brand.orEmpty().normalizeFoodText()
        val queryTokens = normalizedQuery.tokens()
        val nameTokens = normalizedName.tokens()
        val brandTokens = normalizedBrand.tokens()

        val exactName = normalizedName == normalizedQuery
        val exactNameBonus = when {
            exactName -> 65
            normalizedName.startsWith(normalizedQuery) -> 52
            normalizedQuery in normalizedName -> 40
            else -> 0
        }
        val exactBrandBonus = when {
            normalizedBrand == normalizedQuery -> 24
            queryTokens.any { it in brandTokens } -> 14
            else -> 0
        }
        val overlap = queryTokens.count { it in nameTokens } * 7
        val fuzzy = queryTokens.sumOf { queryToken ->
            nameTokens.maxOfOrNull { nameToken ->
                tokenSimilarity(queryToken, nameToken)
            } ?: 0
        }
        val barcodeBonus = if (barcode.isNotBlank()) 4 else 0
        return (exactNameBonus + exactBrandBonus + overlap + fuzzy + barcodeBonus).coerceAtMost(100)
    }

    private fun String.tokens(): Set<String> =
        normalizeFoodText()
            .split(" ")
            .filter { it.length > 2 }
            .toSet()

    private fun tokenSimilarity(a: String, b: String): Int {
        if (a == b) return 8
        if (a.contains(b) || b.contains(a)) return 5
        val distance = levenshtein(a, b)
        val maxLength = max(a.length, b.length)
        return when {
            maxLength <= 3 && distance == 1 -> 3
            distance == 1 -> 5
            distance == 2 -> 4
            distance == 3 -> 2
            else -> 0
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var previous = i
            dp[0] = i + 1
            for (j in b.indices) {
                val current = dp[j + 1]
                val cost = if (a[i] == b[j]) 0 else 1
                dp[j + 1] = minOf(
                    dp[j + 1] + 1,
                    dp[j] + 1,
                    previous + cost
                )
                previous = current
            }
        }
        return dp[b.length]
    }

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
        const val EXTERNAL_ACCEPT_THRESHOLD = 85
    }
}
