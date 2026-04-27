package com.example.comfood.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class OpenFoodFactsService(
    private val brandProfiles: List<BrandProfile>,
    private val localMenuItems: List<LocalMenuItem>,
    private val usdaService: UsdaFoodDataCentralService? = null
) {
    private val localMenuMatcher = LocalMenuMatcher(brandProfiles, localMenuItems)
    private val parser = RuleBasedMealParser(brandProfiles)
    private val heuristicAiFallback = HeuristicAiFallback()

    suspend fun estimateMeal(query: String): MealLookupResult {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return MealLookupResult.Failure("Add a meal description to estimate macros.")
        }

        val parsed = parser.parse(cleaned)
        val matchedBrand = parsed.brand ?: findBrand(parsed.normalized)
        val variants = buildSearchVariants(cleaned, parsed, matchedBrand)
        val candidates = mutableListOf<MealCandidate>()

        when (val localResult = localMenuMatcher.matchDetailed(cleaned)) {
            is LocalMenuMatchResult.Exact -> {
                candidates += MealCandidate(
                    product = ProductInfo(
                        barcode = "",
                        name = localResult.item.name,
                        brand = localResult.item.restaurant,
                        ingredientsText = null,
                        sourceUrl = "On-device restaurant catalog",
                        macrosPer100g = localResult.item.macros,
                        nutritionPerServing = null,
                        matchedQueryScore = 100
                    ),
                    explanation = "Matched against the on-device restaurant menu catalog.",
                    confidence = 100
                )
            }

            is LocalMenuMatchResult.Ambiguous -> {
                localResult.candidates.forEachIndexed { index, item ->
                    candidates += MealCandidate(
                        product = ProductInfo(
                            barcode = "",
                            name = item.name,
                            brand = item.restaurant,
                            ingredientsText = null,
                            sourceUrl = "On-device restaurant catalog",
                            macrosPer100g = item.macros,
                            nutritionPerServing = null,
                            matchedQueryScore = 92 - index
                        ),
                        explanation = "Likely local menu match. Add more detail if you want a tighter guess.",
                        confidence = 92 - index
                    )
                }
            }

            is LocalMenuMatchResult.NoMatch -> Unit
        }

        runCatching {
            val products = variants
                .flatMap { variant -> fetchProducts(searchUrlFor(variant)) }
                .distinctBy { "${it.barcode}-${it.name}-${it.brand}" }
            candidates += products.map { product ->
                val score = product.match(parsed.normalized)
                MealCandidate(
                    product = product.copy(matchedQueryScore = score),
                    explanation = if (score >= 28) {
                        "Strong Open Food Facts match after query cleanup."
                    } else {
                        "Possible Open Food Facts match."
                    },
                    confidence = score
                )
            }
        }

        runCatching {
            val usdaProducts = usdaService?.searchFoods(variants).orEmpty()
            candidates += usdaProducts.map { product ->
                val score = product.match(parsed.normalized)
                MealCandidate(
                    product = product.copy(matchedQueryScore = score),
                    explanation = "Possible USDA FoodData Central match.",
                    confidence = score
                )
            }
        }

        fallbackFor(parsed.normalized)?.let { fallback ->
            candidates += MealCandidate(
                product = fallback,
                explanation = "Closest local fallback when exact brand or product data is thin.",
                confidence = fallback.match(parsed.normalized)
            )
        }

        val distinctCandidates = candidates
            .filter { it.product.macrosPer100g != null }
            .sortedByDescending { it.confidence }
            .distinctBy { "${it.product.name.normalizedText()}|${it.product.brand.orEmpty().normalizedText()}" }
            .take(6)

        return if (distinctCandidates.isNotEmpty()) {
            MealLookupResult.Success(distinctCandidates)
        } else {
            MealLookupResult.Failure("I couldn't find a close enough macro estimate for that meal yet.")
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

    private fun ProductInfo.match(query: String): Int {
        val normalizedQuery = query.normalizedText()
        val queryTokens = query.tokens()
        val normalizedBrand = brand.orEmpty().normalizedText()
        val normalizedName = name.normalizedText()
        val productText = buildString {
            append(name)
            append(' ')
            append(brand.orEmpty())
        }.normalizedText()
        val productTokens = productText.tokens()

        val exactOverlap = queryTokens.count { it in productTokens } * 8
        val fuzzyOverlap = queryTokens.sumOf { token ->
            productTokens.maxOfOrNull { productToken ->
                tokenSimilarity(token, productToken)
            } ?: 0
        }
        val phraseBonus = if (productText.contains(normalizedQuery)) 12 else 0
        val exactBrandBonus = when {
            normalizedBrand == normalizedQuery -> 22
            normalizedQuery in normalizedBrand -> 16
            queryTokens.any { it in normalizedBrand.tokens() } -> 10
            else -> 0
        }
        val exactNameBonus = when {
            normalizedName == normalizedQuery -> 18
            normalizedName.startsWith(normalizedQuery) -> 12
            normalizedQuery in normalizedName -> 8
            else -> 0
        }
        val macroBonus = if (macrosPer100g != null) 4 else 0

        return exactOverlap + fuzzyOverlap + phraseBonus + exactBrandBonus + exactNameBonus + macroBonus
    }

    private fun findBrand(query: String): BrandProfile? =
        brandProfiles.maxByOrNull { brand ->
            brand.aliases.maxOfOrNull { alias ->
                when {
                    query.contains(alias.normalizedText()) -> 20
                    else -> query.tokens().sumOf { queryToken ->
                        alias.normalizedText().tokens().maxOfOrNull { aliasToken ->
                            tokenSimilarity(queryToken, aliasToken)
                        } ?: 0
                    }
                }
            } ?: 0
        }?.takeIf { brand ->
            brand.aliases.any { alias ->
                query.contains(alias.normalizedText()) ||
                    query.tokens().any { queryToken ->
                        alias.normalizedText().tokens().any { aliasToken ->
                            tokenSimilarity(queryToken, aliasToken) >= 4
                        }
                    }
            }
        }

    private fun buildSearchVariants(original: String, parsed: ParsedMealQuery, matchedBrand: BrandProfile?): List<String> {
        val variants = linkedSetOf<String>()
        variants += original.trim()
        variants += heuristicAiFallback.rewriteQueries(parsed)

        val withoutStopWords = parsed.normalized
            .split(" ")
            .filterNot { it in stopWords }
            .joinToString(" ")
            .trim()
        if (withoutStopWords.isNotBlank()) variants += withoutStopWords

        matchedBrand?.let { brand ->
            val mealOnly = parsed.normalized.replace(brand.canonical, "").trim()
            variants += brand.canonical
            if (mealOnly.isNotBlank()) {
                variants += "${brand.canonical} $mealOnly"
                variants += mealOnly
                brand.menuHints.forEach { hint ->
                    if (mealOnly.tokens().any { mealToken ->
                            hint.normalizedText().tokens().any { hintToken ->
                                tokenSimilarity(mealToken, hintToken) >= 4
                            }
                        }
                    ) {
                        variants += "${brand.canonical} $hint"
                    }
                }
            }
        }

        packagedQueryHints.forEach { (brand, hints) ->
            if (parsed.normalized == brand || parsed.normalized.startsWith("$brand ")) {
                hints.forEach { hint -> variants += hint }
            }
        }

        return variants.filter { it.isNotBlank() }
    }

    private fun String.tokens(): Set<String> =
        normalizedText()
            .split(" ")
            .filter { it.length > 2 }
            .toSet()

    private fun String.normalizedText(): String =
        normalizeFoodText()

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

    private fun fallbackFor(query: String): ProductInfo? {
        val lower = query.lowercase(Locale.US)
        val fallback = localFallbacks.firstOrNull { candidate ->
            candidate.first.any { keyword ->
                keyword in lower || lower.split(" ").any { token -> tokenSimilarity(token, keyword) >= 4 }
            }
        } ?: return null
        return ProductInfo(
            barcode = "",
            name = fallback.second,
            brand = null,
            ingredientsText = null,
            sourceUrl = "On-device fallback",
            macrosPer100g = fallback.third,
            nutritionPerServing = null
        )
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
        val stopWords = setOf(
            "a", "an", "the", "with", "and", "meal", "food", "some", "of", "from", "for"
        )

        val localFallbacks = listOf(
            Triple(setOf("egg", "omelet"), "Egg dish", MacroEstimate(155, 13.0, 1.1, 11.0)),
            Triple(setOf("pizza"), "Pizza slice", MacroEstimate(266, 11.0, 33.0, 10.0)),
            Triple(setOf("chicken", "breast"), "Chicken breast", MacroEstimate(165, 31.0, 0.0, 3.6)),
            Triple(
                setOf("mcdonalds", "chicken", "sandwich"),
                "McDonald's style chicken sandwich",
                MacroEstimate(279, 14.0, 28.0, 12.0)
            ),
            Triple(
                setOf("doritos", "dorito"),
                "Doritos Nacho Cheese Tortilla Chips",
                MacroEstimate(508, 7.1, 59.0, 27.0)
            ),
            Triple(setOf("rice"), "Cooked rice", MacroEstimate(130, 2.7, 28.0, 0.3)),
            Triple(setOf("burger", "hamburger"), "Hamburger patty meal", MacroEstimate(295, 17.0, 30.0, 12.0)),
            Triple(setOf("salad"), "Simple salad", MacroEstimate(80, 2.0, 10.0, 3.0)),
            Triple(setOf("protein", "shake"), "Protein shake", MacroEstimate(160, 30.0, 6.0, 3.0))
        )

        val packagedQueryHints = mapOf(
            "doritos" to listOf(
                "doritos",
                "doritos chips",
                "doritos tortilla chips",
                "doritos nacho cheese"
            )
        )
    }
}
