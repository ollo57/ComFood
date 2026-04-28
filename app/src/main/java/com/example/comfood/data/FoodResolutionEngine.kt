package com.example.comfood.data

import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class FoodResolutionEngine(
    private val localMenuItems: List<LocalMenuItem>,
    ingredientRules: List<IngredientRule>
) {
    private val structuredCatalog: List<FoodCatalogEntry> = buildStructuredCatalog(localMenuItems, ingredientRules)
    private val baseCatalog: List<FoodCatalogEntry> = structuredCatalog.filter { it.category == FoodCatalogCategory.BaseFood }
    private val ingredientCatalog: List<FoodCatalogEntry> = structuredCatalog.filter { it.category == FoodCatalogCategory.Ingredient }
    private val brandedCatalog: List<FoodCatalogEntry> = structuredCatalog.filter { it.category == FoodCatalogCategory.BrandedMeal }

    private val protectedConjunctionPhrases: Set<String> = structuredCatalog
        .flatMap { entry ->
            buildList {
                val canonical = entry.canonicalName.normalizeFoodText()
                if (" and " in canonical) add(canonical)
                entry.aliases.forEach { alias ->
                    val normalized = alias.normalizeFoodText()
                    if (" and " in normalized) add(normalized)
                }
            }
        }
        .toSet()

    fun catalogSnapshot(): List<FoodCatalogEntry> = structuredCatalog

    fun resolveOffline(input: String): FoodResolutionResult {
        val normalizedForBlankCheck = input.normalizeFoodText()
        if (normalizedForBlankCheck.isBlank()) {
            val empty = MealComposition(
                originalInput = input,
                foods = emptyList(),
                ingredients = emptyList(),
                estimatedCalories = null,
                overallConfidence = 0,
                usedExternalLookup = false
            )
            return FoodResolutionResult(
                composition = empty,
                offlineCandidates = emptyList(),
                unresolvedSegments = listOf(input),
                needsExternalFallback = true
            )
        }

        val rawForParsing = input
            .lowercase(Locale.US)
            .replace("&", " and ")
        val clauses = decomposeMeal(rawForParsing)
        val foods = mutableListOf<ResolvedFoodItem>()
        val ingredients = mutableListOf<ResolvedIngredient>()
        val unresolved = mutableListOf<String>()

        clauses.forEach { clause ->
            val resolvedPrimary = mutableListOf<ResolvedFoodItem>()
            clause.foodParts.forEach { part ->
                when (val resolution = resolveFoodPhrase(part)) {
                    is FoodPhraseResolution.Resolved -> {
                        foods += resolution.food
                        resolvedPrimary += resolution.food
                    }
                    is FoodPhraseResolution.Unresolved -> unresolved += resolution.phrase
                }
            }

            if (clause.withParts.isNotEmpty()) {
                val parentFoods = resolvedPrimary.ifEmpty { foods.takeLast(1) }
                clause.withParts.forEach { withPart ->
                    when (val classification = classifyWithPart(withPart)) {
                        is WithPartClassification.AsFood -> {
                            when (val resolution = resolveFoodPhrase(withPart)) {
                                is FoodPhraseResolution.Resolved -> {
                                    foods += resolution.food
                                }
                                is FoodPhraseResolution.Unresolved -> unresolved += resolution.phrase
                            }
                        }
                        is WithPartClassification.AsIngredient -> {
                            if (parentFoods.isNotEmpty()) {
                                parentFoods.forEach { parent ->
                                    val ingredient = classifyIngredient(withPart, parent.id)
                                    ingredients += ingredient
                                }
                            } else {
                                ingredients += classifyIngredient(withPart, parentFoodId = null)
                            }
                        }
                    }
                }
            }
        }

        val foodsWithIngredients = foods.map { food ->
            val linked = ingredients.filter { it.parentFoodId == food.id }
            val ingredientCalories = linked.mapNotNull { it.estimatedCalories }.sum().takeIf { it > 0 } ?: 0
            val totalCalories = (food.estimatedCalories ?: 0) + ingredientCalories
            val ingredientNutrition = linked
                .mapNotNull { it.estimatedNutrition }
                .fold(null as NutritionEstimate?) { acc, nutrition -> acc.merge(nutrition) }
            val totalNutrition = food.estimatedNutrition.merge(ingredientNutrition)
            food.copy(
                ingredients = linked,
                estimatedCalories = totalCalories.takeIf { it > 0 },
                estimatedNutrition = totalNutrition
            )
        }

        val totalCalories = foodsWithIngredients.mapNotNull { it.estimatedCalories }.sum().takeIf { it > 0 }
        val overallConfidence = if (foodsWithIngredients.isEmpty()) {
            0
        } else {
            foodsWithIngredients.map { it.confidence }.average().roundToInt()
        }

        val composition = MealComposition(
            originalInput = input,
            foods = foodsWithIngredients,
            ingredients = ingredients,
            estimatedCalories = totalCalories,
            overallConfidence = overallConfidence,
            usedExternalLookup = false
        )

        val candidates = buildOfflineCandidates(composition)
        val needsFallback = foodsWithIngredients.isEmpty() ||
            (overallConfidence < MEDIUM_CONFIDENCE && unresolved.isNotEmpty())

        return FoodResolutionResult(
            composition = composition,
            offlineCandidates = candidates,
            unresolvedSegments = unresolved.distinct(),
            needsExternalFallback = needsFallback
        )
    }

    private fun resolveFoodPhrase(rawPhrase: String): FoodPhraseResolution {
        val quantity = parseQuantity(rawPhrase)
        val cleaned = rawPhrase
            .normalizeFoodText()
            .removeWords(quantity.removalTokens)
            .trim()
        if (cleaned.isBlank()) return FoodPhraseResolution.Unresolved(rawPhrase)

        val best = bestFoodMatch(cleaned) ?: return FoodPhraseResolution.Unresolved(cleaned)
        if (!isAcceptedFoodMatch(cleaned, best)) return FoodPhraseResolution.Unresolved(cleaned)

        val foodId = UUID.randomUUID().toString()
        val calories = (best.entry.caloriesPerUnit * quantity.multiplier).roundToInt().takeIf { it > 0 }
        val label = best.entry.canonicalName.replaceFirstChar { it.uppercase() }
        val quantityLabel = quantity.displayTextWithModifier()

        return FoodPhraseResolution.Resolved(
            ResolvedFoodItem(
                id = foodId,
                label = label,
                brand = best.entry.sourceBrand,
                quantityText = quantityLabel,
                quantityMultiplier = quantity.multiplier,
                estimatedCalories = calories,
                estimatedNutrition = best.entry.nutritionPerUnit?.scaleBy(quantity.multiplier),
                confidence = best.confidence,
                matchType = best.matchType,
                ingredients = emptyList()
            )
        )
    }

    private fun bestFoodMatch(query: String): CatalogMatch? {
        val queryTokens = query.tokens()
        if (queryTokens.isEmpty()) return null

        val baseMatches = baseCatalog.map { scoreMatch(query, queryTokens, it) }
            .sortedByDescending { it.confidence }
        val brandedMatches = brandedCatalog.map { scoreMatch(query, queryTokens, it) }
            .sortedByDescending { it.confidence }

        val bestBase = baseMatches.firstOrNull()
        val bestBranded = brandedMatches.firstOrNull()

        if (bestBranded == null) return bestBase
        if (bestBase == null) return bestBranded

        val brandedExplicit = explicitDishMention(query, bestBranded.entry)
        return if (brandedExplicit && bestBranded.confidence >= HIGH_CONFIDENCE) {
            if (bestBranded.confidence >= bestBase.confidence + 8) bestBranded else bestBase
        } else {
            bestBase
        }
    }

    private fun isAcceptedFoodMatch(query: String, match: CatalogMatch): Boolean {
        val entry = match.entry
        if (violatesNonExpansion(query, match)) return false

        return when (entry.category) {
            FoodCatalogCategory.BrandedMeal -> {
                explicitDishMention(query, entry) && match.confidence >= HIGH_CONFIDENCE
            }
            FoodCatalogCategory.BaseFood -> {
                match.confidence >= BASE_ACCEPT_CONFIDENCE
            }
            FoodCatalogCategory.Ingredient -> {
                match.confidence >= INGREDIENT_AS_FOOD_CONFIDENCE
            }
        }
    }

    private fun violatesNonExpansion(query: String, match: CatalogMatch): Boolean {
        val entry = match.entry
        val queryTokens = query.tokens()
        val entryTokens = entry.canonicalName.normalizeFoodText().tokens()
        val explicit = explicitDishMention(query, entry)

        if (entry.category == FoodCatalogCategory.BrandedMeal && !explicit) return true

        val genericHead = queryTokens.size <= 2 && queryTokens.all { it in GENERIC_BASE_TERMS }
        if (genericHead && entryTokens.size > queryTokens.size && !explicit) return true

        if (entryTokens.size > queryTokens.size + 1 && !explicit) return true

        return false
    }

    private fun classifyWithPart(raw: String): WithPartClassification {
        val normalized = raw.normalizeFoodText()
        if (normalized.isBlank()) return WithPartClassification.AsIngredient

        if (normalized.tokens().any { it in INGREDIENT_PRIORITY_TOKENS }) {
            return WithPartClassification.AsIngredient
        }

        val ingredientMatch = ingredientCatalog
            .map { scoreMatch(normalized, normalized.tokens(), it) }
            .maxByOrNull { it.confidence }
        val baseMatch = baseCatalog
            .map { scoreMatch(normalized, normalized.tokens(), it) }
            .maxByOrNull { it.confidence }

        if (baseMatch != null && baseMatch.confidence >= BASE_ACCEPT_CONFIDENCE) {
            if (ingredientMatch == null || baseMatch.confidence >= ingredientMatch.confidence + 10) {
                if (normalized.tokens().any { it in SIDE_FOOD_TOKENS }) {
                    return WithPartClassification.AsFood
                }
                if (normalized.tokens().size >= 2 && baseMatch.entry.category == FoodCatalogCategory.BaseFood) {
                    return WithPartClassification.AsFood
                }
            }
        }

        return WithPartClassification.AsIngredient
    }

    private fun classifyIngredient(raw: String, parentFoodId: String?): ResolvedIngredient {
        val quantity = parseQuantity(raw)
        val cleaned = raw.normalizeFoodText().removeWords(quantity.removalTokens).trim()
        val normalized = if (cleaned.isBlank()) raw.normalizeFoodText() else cleaned
        val queryTokens = normalized.tokens()

        val match = ingredientCatalog
            .map { scoreMatch(normalized, queryTokens, it) }
            .maxByOrNull { it.confidence }

        val label = match?.entry?.canonicalName?.replaceFirstChar { it.uppercase() }
            ?: normalized.replaceFirstChar { it.uppercase() }

        return ResolvedIngredient(
            id = UUID.randomUUID().toString(),
            label = label,
            quantityText = quantity.displayTextWithModifier(),
            confidence = (match?.confidence ?: 52).coerceIn(35, 95),
            parentFoodId = parentFoodId,
            estimatedCalories = match?.entry?.caloriesPerUnit?.let { (it * quantity.multiplier).roundToInt().takeIf { c -> c > 0 } },
            estimatedNutrition = match?.entry?.nutritionPerUnit?.scaleBy(quantity.multiplier)
        )
    }

    private fun scoreMatch(query: String, queryTokens: Set<String>, entry: FoodCatalogEntry): CatalogMatch {
        val normalizedCanonical = entry.canonicalName.normalizeFoodText()
        val normalizedAliases = entry.aliases.map { it.normalizeFoodText() }
        val candidateTokens = (setOf(normalizedCanonical) + normalizedAliases)
            .flatMap { it.tokens() }
            .toSet()

        val exact = query == normalizedCanonical
        val aliasExact = normalizedAliases.any { it == query }

        val tokenOverlap = queryTokens.intersect(candidateTokens).size.toDouble()
        val coverageQuery = if (queryTokens.isEmpty()) 0.0 else tokenOverlap / queryTokens.size
        val coverageEntry = if (candidateTokens.isEmpty()) 0.0 else tokenOverlap / candidateTokens.size

        val fuzzy = if (queryTokens.isEmpty()) 0.0 else {
            queryTokens.map { token ->
                candidateTokens.maxOfOrNull { candidate -> tokenSimilarity(token, candidate) } ?: 0.0
            }.average()
        }

        val contains = normalizedCanonical.contains(query) || query.contains(normalizedCanonical)
        val confidence = when {
            exact -> 100
            aliasExact -> 94
            else -> {
                var score = (coverageQuery * 48.0) + (coverageEntry * 22.0) + (fuzzy * 30.0)
                if (contains) score += 6.0
                if (entry.category == FoodCatalogCategory.BrandedMeal) score -= 10.0
                score.roundToInt().coerceIn(0, 100)
            }
        }

        val matchType = when {
            exact -> FoodMatchType.Exact
            aliasExact -> FoodMatchType.Alias
            contains || coverageQuery >= 0.7 -> FoodMatchType.Partial
            else -> FoodMatchType.Fuzzy
        }

        return CatalogMatch(entry = entry, confidence = confidence, matchType = matchType)
    }

    private fun explicitDishMention(query: String, entry: FoodCatalogEntry): Boolean {
        val normalized = query.normalizeFoodText()
        val canonical = entry.canonicalName.normalizeFoodText()
        if (normalized == canonical) return true
        if (normalized.contains(canonical) && canonical.tokens().size >= 2) return true

        return entry.aliases.any { alias ->
            val normalizedAlias = alias.normalizeFoodText()
            normalizedAlias.tokens().size >= 2 && normalized.contains(normalizedAlias)
        }
    }

    private fun parseQuantity(raw: String): QuantityInfo {
        val rawLower = raw.lowercase(Locale.US)
        val normalized = raw.normalizeFoodText()
        var multiplier = 1.0
        var unit: String? = null
        val removalTokens = mutableSetOf<String>()
        val modifiers = mutableListOf<String>()

        NUMBER_WORD_VALUES.forEach { (word, value) ->
            if (Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(normalized)) {
                multiplier = value
                removalTokens += word
            }
        }

        Regex("(?<!\\d)(\\d+(?:\\.\\d+)?)(?!\\d)").find(rawLower)?.let { numeric ->
            numeric.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { value ->
                multiplier = value
                numeric.value.normalizeFoodText().split(" ").forEach { token ->
                    if (token.isNotBlank()) removalTokens += token
                }
            }
        }

        if (Regex("\\bhalf\\b").containsMatchIn(normalized)) {
            multiplier = 0.5
            removalTokens += "half"
        }

        if (Regex("\\b(a little|little)\\b").containsMatchIn(normalized)) {
            multiplier = 0.5
            removalTokens += "a"
            removalTokens += "little"
        }

        if (Regex("\\b(handful)\\b").containsMatchIn(normalized)) {
            if (multiplier == 1.0) multiplier = 1.0
            unit = "handful"
            removalTokens += "handful"
        }

        if (Regex("\\b(some|several|few)\\b").containsMatchIn(normalized) && multiplier == 1.0) {
            multiplier = 1.0
            removalTokens += "some"
            removalTokens += "several"
            removalTokens += "few"
        }

        COUNT_MULTIPLIERS.forEach { (word, factor) ->
            if (Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(normalized)) {
                multiplier *= factor
                removalTokens += word
            }
        }

        SERVING_UNITS.forEach { candidateUnit ->
            if (Regex("\\b${Regex.escape(candidateUnit)}s?\\b").containsMatchIn(normalized)) {
                unit = candidateUnit
                removalTokens += candidateUnit
                removalTokens += "${candidateUnit}s"
            }
        }

        if (Regex("\\bof\\b").containsMatchIn(normalized)) {
            removalTokens += "of"
        }

        FOOD_MODIFIERS.forEach { modifier ->
            if (Regex("\\b${Regex.escape(modifier)}\\b").containsMatchIn(normalized)) {
                modifiers += modifier
                removalTokens += modifier
            }
        }

        return QuantityInfo(
            multiplier = multiplier.coerceAtLeast(0.1),
            unit = unit,
            modifiers = modifiers,
            removalTokens = removalTokens
        )
    }

    private fun QuantityInfo.displayTextWithModifier(): String? {
        val quantityText = when {
            multiplier == 1.0 && unit == null -> null
            unit != null -> "${multiplier.prettyQuantity()} $unit"
            else -> multiplier.prettyQuantity()
        }
        val modifierText = modifiers.distinct().joinToString(" ").ifBlank { null }
        return listOfNotNull(quantityText, modifierText).joinToString(" ").ifBlank { null }
    }

    private fun Double.prettyQuantity(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else String.format(Locale.US, "%.1f", this)

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.72
        val distance = levenshtein(a, b)
        val maxLength = maxOf(a.length, b.length).coerceAtLeast(1)
        return (1.0 - (distance.toDouble() / maxLength.toDouble())).coerceIn(0.0, 1.0)
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
                dp[j + 1] = minOf(dp[j + 1] + 1, dp[j] + 1, previous + cost)
                previous = current
            }
        }
        return dp[b.length]
    }

    private fun decomposeMeal(normalizedInput: String): List<MealClause> {
        val segments = normalizedInput.split(Regex("[;\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
        if (segments.isEmpty()) return emptyList()

        return segments.map { segment ->
            val withParts = segment.split(" with ")
            val primarySegment = withParts.first().trim()
            val ingredientSegment = withParts.drop(1).joinToString(" with ").trim()

            MealClause(
                foodParts = splitFoodConjunctions(primarySegment),
                withParts = splitIngredientConjunctions(ingredientSegment)
            )
        }
    }

    private fun splitFoodConjunctions(segment: String): List<String> {
        if (segment.isBlank()) return emptyList()
        var protected = segment
        val placeholders = mutableMapOf<String, String>()
        protectedConjunctionPhrases.forEachIndexed { index, phrase ->
            if (phrase in protected) {
                val placeholder = "__protected_${index}__"
                placeholders[placeholder] = phrase
                protected = protected.replace(phrase, placeholder)
            }
        }

        return protected
            .split(Regex(",|\\b(and|plus|&)\\b"))
            .map { part ->
                var restored = part.trim()
                placeholders.forEach { (placeholder, original) ->
                    restored = restored.replace(placeholder, original)
                }
                restored
            }
            .filter { it.isNotBlank() }
    }

    private fun splitIngredientConjunctions(segment: String): List<String> {
        if (segment.isBlank()) return emptyList()
        return segment
            .split(',')
            .flatMap { sub -> sub.split(Regex("\\b(and|plus|&)\\b")) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun String.tokens(): Set<String> =
        normalizeFoodText()
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()

    private fun String.removeWords(words: Set<String>): String {
        if (words.isEmpty()) return this
        return normalizeFoodText()
            .split(" ")
            .filterNot { it in words }
            .joinToString(" ")
            .trim()
    }

    private fun buildOfflineCandidates(composition: MealComposition): List<MealCandidate> {
        if (composition.foods.isEmpty()) return emptyList()

        val perFood = composition.foods.map { food ->
            val sourceMenuItem = localMenuItems.firstOrNull { item ->
                item.name.equals(food.label, ignoreCase = true) &&
                    (food.brand == null || item.restaurant.equals(food.brand, ignoreCase = true))
            }
            val macros = sourceMenuItem?.macros?.let { base ->
                MacroEstimate(
                    calories = food.estimatedCalories ?: base.calories,
                    proteinGrams = base.proteinGrams * food.quantityMultiplier,
                    carbsGrams = base.carbsGrams * food.quantityMultiplier,
                    fatGrams = base.fatGrams * food.quantityMultiplier
                )
            } ?: food.estimatedCalories?.let { kcal ->
                MacroEstimate(kcal, 0.0, 0.0, 0.0)
            }

            val nutrition = food.estimatedNutrition

            MealCandidate(
                product = ProductInfo(
                    barcode = "",
                    name = food.label,
                    brand = food.brand,
                    ingredientsText = food.ingredients.joinToString(", ") { it.label }.ifBlank { null },
                    sourceUrl = "On-device food resolution engine",
                    macrosPer100g = macros,
                    nutritionPerServing = nutrition,
                    matchedQueryScore = food.confidence
                ),
                explanation = "Resolved ${food.matchType.name.lowercase(Locale.US)} food item.",
                confidence = food.confidence,
                mealComposition = composition
            )
        }

        val summary = composition.estimatedCalories?.let { kcal ->
            val summaryNutrition = composition.foods
                .mapNotNull { it.estimatedNutrition }
                .fold(null as NutritionEstimate?) { acc, nutrition -> acc.merge(nutrition) }
            MealCandidate(
                product = ProductInfo(
                    barcode = "",
                    name = "Structured meal",
                    brand = null,
                    ingredientsText = composition.ingredients.joinToString(", ") { it.label }.ifBlank { null },
                    sourceUrl = "On-device food resolution engine",
                    macrosPer100g = MacroEstimate(kcal, 0.0, 0.0, 0.0),
                    nutritionPerServing = summaryNutrition,
                    matchedQueryScore = composition.overallConfidence
                ),
                explanation = "Deterministic decomposition with ${composition.foods.size} food item(s).",
                confidence = composition.overallConfidence,
                mealComposition = composition
            )
        }

        return (listOfNotNull(summary) + perFood)
            .sortedByDescending { it.confidence }
            .take(6)
    }

    private fun buildStructuredCatalog(
        menuItems: List<LocalMenuItem>,
        ingredientRules: List<IngredientRule>
    ): List<FoodCatalogEntry> {
        val entries = mutableListOf<FoodCatalogEntry>()

        menuItems.forEach { item ->
            val isGeneric = item.restaurant.equals("generic", ignoreCase = true)
            val category = if (isGeneric) FoodCatalogCategory.BaseFood else FoodCatalogCategory.BrandedMeal
            entries += FoodCatalogEntry(
                canonicalName = item.name,
                aliases = item.aliases,
                category = category,
                typicalServingUnit = if (category == FoodCatalogCategory.BrandedMeal) "serving" else inferServingUnit(item.name),
                caloriesPerUnit = item.macros.calories.toDouble(),
                nutritionPerUnit = nutritionProfileFor(item.name),
                sourceBrand = item.restaurant.takeUnless { it.equals("generic", ignoreCase = true) }
            )
        }

        BASE_FOOD_SEEDS.forEach { seed ->
            entries += FoodCatalogEntry(
                canonicalName = seed.canonical,
                aliases = seed.aliases,
                category = FoodCatalogCategory.BaseFood,
                typicalServingUnit = seed.unit,
                caloriesPerUnit = seed.calories,
                nutritionPerUnit = nutritionProfileFor(seed.canonical),
                sourceBrand = null
            )
        }

        ingredientRules.forEach { rule ->
            entries += FoodCatalogEntry(
                canonicalName = rule.label,
                aliases = rule.aliases,
                category = FoodCatalogCategory.Ingredient,
                typicalServingUnit = inferServingUnit(rule.label),
                caloriesPerUnit = INGREDIENT_CALORIE_MAP[rule.label.normalizeFoodText()] ?: 0.0,
                nutritionPerUnit = nutritionProfileFor(rule.label),
                sourceBrand = null
            )
        }

        COMMON_INGREDIENT_SEEDS.forEach { seed ->
            entries += FoodCatalogEntry(
                canonicalName = seed.canonical,
                aliases = seed.aliases,
                category = FoodCatalogCategory.Ingredient,
                typicalServingUnit = seed.unit,
                caloriesPerUnit = seed.calories,
                nutritionPerUnit = nutritionProfileFor(seed.canonical),
                sourceBrand = null
            )
        }

        return entries
            .groupBy { "${it.canonicalName.normalizeFoodText()}|${it.category.name}" }
            .map { (_, grouped) ->
                val first = grouped.first()
                val aliases = grouped.flatMap { it.aliases + it.canonicalName }.distinct()
                FoodCatalogEntry(
                    canonicalName = first.canonicalName,
                    aliases = aliases,
                    category = first.category,
                    typicalServingUnit = first.typicalServingUnit,
                    caloriesPerUnit = grouped.maxOf { it.caloriesPerUnit },
                    nutritionPerUnit = grouped.mapNotNull { it.nutritionPerUnit }.firstOrNull(),
                    sourceBrand = first.sourceBrand
                )
            }
    }

    private fun inferServingUnit(name: String): String {
        val normalized = name.normalizeFoodText()
        return when {
            normalized.contains("bread") || normalized.contains("toast") -> "slice"
            normalized.contains("egg") -> "piece"
            normalized.contains("rice") || normalized.contains("pasta") -> "cup"
            normalized.contains("cheese") -> "slice"
            normalized.contains("milk") -> "cup"
            normalized.contains("butter") -> "tbsp"
            normalized.contains("fries") -> "portion"
            normalized.contains("chicken") || normalized.contains("beef") || normalized.contains("salmon") || normalized.contains("fish") -> "3 oz"
            normalized.contains("apple") || normalized.contains("banana") -> "fruit"
            normalized.contains("potato") -> "piece"
            normalized.contains("nuts") || normalized.contains("almonds") -> "oz"
            normalized.contains("mayo") || normalized.contains("ketchup") || normalized.contains("sauce") || normalized.contains("dressing") -> "tbsp"
            normalized.contains("mustard") -> "tsp"
            else -> "serving"
        }
    }

    private fun nutritionProfileFor(label: String): NutritionEstimate? {
        val normalized = label.normalizeFoodText()
        return NUTRITION_PROFILE_MAP[normalized]
            ?: NUTRITION_PROFILE_MAP.entries.firstOrNull { (key, _) ->
                normalized.contains(key) || key.contains(normalized)
            }?.value
    }

    private fun NutritionEstimate.scaleBy(multiplier: Double): NutritionEstimate =
        NutritionEstimate(
            fiberGrams = fiberGrams * multiplier,
            sugarGrams = sugarGrams * multiplier,
            sodiumMg = sodiumMg * multiplier,
            potassiumMg = potassiumMg * multiplier,
            calciumMg = calciumMg * multiplier,
            ironMg = ironMg * multiplier,
            vitaminCMg = vitaminCMg * multiplier,
            vitaminDMcg = vitaminDMcg * multiplier,
            vitaminAMcg = vitaminAMcg * multiplier,
            vitaminB12Mcg = vitaminB12Mcg * multiplier
        )

    private fun NutritionEstimate?.merge(other: NutritionEstimate?): NutritionEstimate? {
        if (this == null) return other
        if (other == null) return this
        return NutritionEstimate(
            fiberGrams = this.fiberGrams + other.fiberGrams,
            sugarGrams = this.sugarGrams + other.sugarGrams,
            sodiumMg = this.sodiumMg + other.sodiumMg,
            potassiumMg = this.potassiumMg + other.potassiumMg,
            calciumMg = this.calciumMg + other.calciumMg,
            ironMg = this.ironMg + other.ironMg,
            vitaminCMg = this.vitaminCMg + other.vitaminCMg,
            vitaminDMcg = this.vitaminDMcg + other.vitaminDMcg,
            vitaminAMcg = this.vitaminAMcg + other.vitaminAMcg,
            vitaminB12Mcg = this.vitaminB12Mcg + other.vitaminB12Mcg
        )
    }

    private data class MealClause(
        val foodParts: List<String>,
        val withParts: List<String>
    )

    private data class CatalogMatch(
        val entry: FoodCatalogEntry,
        val confidence: Int,
        val matchType: FoodMatchType
    )

    private data class QuantityInfo(
        val multiplier: Double,
        val unit: String?,
        val modifiers: List<String>,
        val removalTokens: Set<String>
    )

    private sealed interface FoodPhraseResolution {
        data class Resolved(val food: ResolvedFoodItem) : FoodPhraseResolution
        data class Unresolved(val phrase: String) : FoodPhraseResolution
    }

    private sealed interface WithPartClassification {
        data object AsFood : WithPartClassification
        data object AsIngredient : WithPartClassification
    }

    private data class CatalogSeed(
        val canonical: String,
        val aliases: List<String>,
        val unit: String,
        val calories: Double
    )

    private companion object {
        private const val HIGH_CONFIDENCE = 85
        private const val BASE_ACCEPT_CONFIDENCE = 58
        private const val INGREDIENT_AS_FOOD_CONFIDENCE = 62
        private const val MEDIUM_CONFIDENCE = 70

        private val STOP_WORDS = setOf(
            "a", "an", "the", "i", "ate", "had", "got", "for", "my", "meal", "food", "to", "of", "on", "portion"
        )

        private val FOOD_MODIFIERS = setOf(
            "fried", "grilled", "toasted", "baked", "roasted", "steamed", "boiled", "scrambled", "poached"
        )

        private val GENERIC_BASE_TERMS = setOf(
            "bread", "egg", "eggs", "rice", "chicken", "cheese", "beef", "potato", "potatoes", "pasta", "milk", "butter",
            "fish", "toast"
        )

        private val SIDE_FOOD_TOKENS = setOf(
            "rice", "potato", "potatoes", "fries", "vegetable", "vegetables", "bread", "pasta", "banana", "nuts"
        )

        private val INGREDIENT_PRIORITY_TOKENS = setOf(
            "lettuce", "mayo", "mayonnaise", "cheese", "pickle", "pickles", "ketchup", "mustard",
            "onion", "tomato", "olives", "croutons", "dressing"
        )

        private val SERVING_UNITS = setOf(
            "gram", "g", "cup", "piece", "slice", "tbsp", "tsp", "portion", "serving"
        )

        private val NUMBER_WORD_VALUES = mapOf(
            "one" to 1.0,
            "two" to 2.0,
            "three" to 3.0,
            "four" to 4.0,
            "five" to 5.0,
            "six" to 6.0,
            "seven" to 7.0,
            "eight" to 8.0,
            "nine" to 9.0,
            "ten" to 10.0,
            "half" to 0.5
        )

        private val COUNT_MULTIPLIERS = mapOf(
            "double" to 2.0,
            "triple" to 3.0
        )

        private val BASE_FOOD_SEEDS = listOf(
            CatalogSeed("Apple", listOf("apple"), "fruit", 95.0),
            CatalogSeed("Banana", listOf("banana"), "fruit", 105.0),
            CatalogSeed("Broccoli", listOf("broccoli"), "cup", 55.0),
            CatalogSeed("Spinach", listOf("spinach"), "cup", 7.0),
            CatalogSeed("Chicken", listOf("chicken", "chicken breast"), "3 oz", 142.0),
            CatalogSeed("Salmon", listOf("salmon"), "3 oz", 155.0),
            CatalogSeed("Bread", listOf("bread"), "slice", 69.0),
            CatalogSeed("Eggs", listOf("egg", "eggs"), "piece", 72.0),
            CatalogSeed("Rice", listOf("rice", "brown rice"), "cup", 218.0),
            CatalogSeed("Cheese", listOf("cheese", "cheddar cheese"), "slice", 113.0),
            CatalogSeed("Beef", listOf("beef"), "3 oz", 175.0),
            CatalogSeed("Potato", listOf("potato", "potatoes"), "piece", 110.0),
            CatalogSeed("Pasta", listOf("pasta"), "cup", 220.0),
            CatalogSeed("Milk", listOf("milk"), "cup", 149.0),
            CatalogSeed("Butter", listOf("butter"), "tbsp", 102.0),
            CatalogSeed("Nuts", listOf("nuts", "mixed nuts", "almonds"), "oz", 164.0),
            CatalogSeed("Fish", listOf("fish"), "3 oz", 140.0),
            CatalogSeed("Toast", listOf("toast"), "slice", 70.0),
            CatalogSeed("French Fries", listOf("fries", "french fries"), "portion", 365.0),
            CatalogSeed("Chicken Sandwich", listOf("chicken sandwich"), "serving", 430.0),
            CatalogSeed("Burger", listOf("burger"), "serving", 250.0),
            CatalogSeed("Pizza", listOf("pizza"), "slice", 313.0),
            CatalogSeed("Salad", listOf("salad"), "cup", 15.0),
            CatalogSeed("Vegetables", listOf("vegetable", "vegetables"), "serving", 50.0),
            CatalogSeed("Sauce", listOf("sauce"), "tbsp", 35.0)
        )

        private val COMMON_INGREDIENT_SEEDS = listOf(
            CatalogSeed("Cheese", listOf("cheese"), "slice", 113.0),
            CatalogSeed("Lettuce", listOf("lettuce"), "serving", 5.0),
            CatalogSeed("Mayo", listOf("mayo", "mayonnaise"), "tbsp", 94.0),
            CatalogSeed("Tomato", listOf("tomato", "tomatoes"), "slice", 4.0),
            CatalogSeed("Onion", listOf("onion", "onions"), "slice", 6.0),
            CatalogSeed("Pickles", listOf("pickle", "pickles"), "piece", 16.0),
            CatalogSeed("Ketchup", listOf("ketchup"), "tbsp", 17.0),
            CatalogSeed("Mustard", listOf("mustard"), "tsp", 3.0),
            CatalogSeed("Olives", listOf("olive", "olives"), "piece", 5.0),
            CatalogSeed("Croutons", listOf("crouton", "croutons"), "tbsp", 12.0),
            CatalogSeed("Dressing", listOf("dressing"), "tbsp", 65.0),
            CatalogSeed("Cheddar Cheese", listOf("cheddar cheese", "cheddar"), "slice", 113.0)
        )

        private val INGREDIENT_CALORIE_MAP = mapOf(
            "cheese" to 113.0,
            "milk" to 149.0,
            "egg" to 72.0,
            "butter" to 102.0,
            "mustard" to 3.0,
            "ketchup" to 17.0,
            "mayo" to 94.0,
            "lettuce" to 5.0,
            "onion" to 6.0,
            "tomato" to 4.0,
            "pickle" to 16.0,
            "olives" to 5.0,
            "croutons" to 12.0,
            "dressing" to 65.0
        )

        private val NUTRITION_PROFILE_MAP = mapOf(
            "apple" to NutritionEstimate(fiberGrams = 4.4, sugarGrams = 19.0, sodiumMg = 2.0, potassiumMg = 195.0, calciumMg = 11.0, ironMg = 0.2, vitaminCMg = 8.4, vitaminAMcg = 3.0),
            "banana" to NutritionEstimate(fiberGrams = 3.1, sugarGrams = 14.0, sodiumMg = 1.0, potassiumMg = 422.0, calciumMg = 6.0, ironMg = 0.3, vitaminCMg = 10.3, vitaminAMcg = 3.0),
            "broccoli" to NutritionEstimate(fiberGrams = 5.1, sugarGrams = 2.2, sodiumMg = 64.0, potassiumMg = 457.0, calciumMg = 62.0, ironMg = 1.0, vitaminCMg = 101.0, vitaminAMcg = 60.0),
            "spinach" to NutritionEstimate(fiberGrams = 0.7, sugarGrams = 0.1, sodiumMg = 24.0, potassiumMg = 167.0, calciumMg = 30.0, ironMg = 0.8, vitaminCMg = 8.4, vitaminAMcg = 141.0),
            "chicken" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.0, sodiumMg = 63.0, potassiumMg = 332.0, calciumMg = 13.0, ironMg = 0.9, vitaminDMcg = 0.1, vitaminAMcg = 6.0, vitaminB12Mcg = 0.3),
            "salmon" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.0, sodiumMg = 48.0, potassiumMg = 534.0, calciumMg = 13.0, ironMg = 1.0, vitaminDMcg = 11.0, vitaminAMcg = 11.0, vitaminB12Mcg = 3.0),
            "egg" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.2, sodiumMg = 71.0, potassiumMg = 69.0, calciumMg = 28.0, ironMg = 0.9, vitaminDMcg = 1.2, vitaminAMcg = 90.0, vitaminB12Mcg = 0.5),
            "eggs" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.2, sodiumMg = 71.0, potassiumMg = 69.0, calciumMg = 28.0, ironMg = 0.9, vitaminDMcg = 1.2, vitaminAMcg = 90.0, vitaminB12Mcg = 0.5),
            "milk" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 12.3, sodiumMg = 93.0, potassiumMg = 366.0, calciumMg = 300.0, ironMg = 0.0, vitaminDMcg = 2.7, vitaminAMcg = 78.0, vitaminB12Mcg = 1.3),
            "bread" to NutritionEstimate(fiberGrams = 1.9, sugarGrams = 1.7, sodiumMg = 148.0, potassiumMg = 71.0, calciumMg = 20.0, ironMg = 0.9),
            "almonds" to NutritionEstimate(fiberGrams = 3.5, sugarGrams = 1.2, sodiumMg = 1.0, potassiumMg = 208.0, calciumMg = 76.0, ironMg = 1.1),
            "nuts" to NutritionEstimate(fiberGrams = 3.5, sugarGrams = 1.2, sodiumMg = 1.0, potassiumMg = 208.0, calciumMg = 76.0, ironMg = 1.1),
            "potato" to NutritionEstimate(fiberGrams = 2.0, sugarGrams = 1.0, sodiumMg = 0.0, potassiumMg = 620.0, calciumMg = 20.0, ironMg = 1.1, vitaminCMg = 27.0),
            "pasta" to NutritionEstimate(fiberGrams = 2.5, sugarGrams = 0.8, sodiumMg = 1.0, potassiumMg = 62.0, calciumMg = 20.0, ironMg = 1.8),
            "beef" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.0, sodiumMg = 55.0, potassiumMg = 300.0, calciumMg = 15.0, ironMg = 2.5, vitaminDMcg = 0.1, vitaminB12Mcg = 2.5),
            "butter" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.0, sodiumMg = 91.0, potassiumMg = 3.0, calciumMg = 3.0, ironMg = 0.0, vitaminAMcg = 97.0, vitaminB12Mcg = 0.02),
            "french fries" to NutritionEstimate(fiberGrams = 4.0, sugarGrams = 0.0, sodiumMg = 260.0, potassiumMg = 570.0, calciumMg = 18.0, ironMg = 0.8, vitaminCMg = 15.0),
            "burger" to NutritionEstimate(fiberGrams = 1.0, sugarGrams = 6.0, sodiumMg = 480.0, potassiumMg = 200.0, calciumMg = 100.0, ironMg = 2.2, vitaminCMg = 1.0, vitaminDMcg = 0.1, vitaminB12Mcg = 1.1),
            "pizza" to NutritionEstimate(fiberGrams = 2.6, sugarGrams = 3.6, sodiumMg = 760.0, potassiumMg = 215.0, calciumMg = 220.0, ironMg = 2.8, vitaminCMg = 1.0, vitaminDMcg = 0.6, vitaminAMcg = 150.0, vitaminB12Mcg = 0.6),
            "salad" to NutritionEstimate(fiberGrams = 1.0, sugarGrams = 2.0, sodiumMg = 20.0, potassiumMg = 160.0, calciumMg = 45.0, ironMg = 0.4, vitaminCMg = 15.0, vitaminAMcg = 230.0),
            "mayo" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 0.1, sodiumMg = 90.0, potassiumMg = 3.0, calciumMg = 2.0, ironMg = 0.0, vitaminB12Mcg = 0.03),
            "ketchup" to NutritionEstimate(fiberGrams = 0.1, sugarGrams = 3.7, sodiumMg = 155.0, potassiumMg = 45.0, calciumMg = 3.0, ironMg = 0.1, vitaminCMg = 0.7, vitaminAMcg = 42.0),
            "mustard" to NutritionEstimate(fiberGrams = 0.2, sugarGrams = 0.1, sodiumMg = 55.0, potassiumMg = 7.0, calciumMg = 0.0, ironMg = 0.1, vitaminCMg = 0.1),
            "pickles" to NutritionEstimate(fiberGrams = 1.4, sugarGrams = 1.4, sodiumMg = 1090.0, potassiumMg = 165.0, calciumMg = 18.0, ironMg = 0.8, vitaminCMg = 3.0),
            "onion" to NutritionEstimate(fiberGrams = 0.3, sugarGrams = 0.7, sodiumMg = 1.0, potassiumMg = 25.0, calciumMg = 10.0, ironMg = 0.03, vitaminCMg = 1.2),
            "tomato" to NutritionEstimate(fiberGrams = 0.3, sugarGrams = 0.7, sodiumMg = 1.0, potassiumMg = 60.0, calciumMg = 10.0, ironMg = 0.07, vitaminCMg = 3.4, vitaminAMcg = 42.0),
            "olives" to NutritionEstimate(fiberGrams = 0.7, sugarGrams = 0.0, sodiumMg = 150.0, potassiumMg = 2.0, calciumMg = 18.0, ironMg = 0.7, vitaminCMg = 0.3),
            "croutons" to NutritionEstimate(fiberGrams = 0.1, sugarGrams = 0.1, sodiumMg = 27.0, potassiumMg = 5.0, calciumMg = 25.0, ironMg = 0.1),
            "dressing" to NutritionEstimate(fiberGrams = 0.0, sugarGrams = 1.0, sodiumMg = 135.0, potassiumMg = 10.0, calciumMg = 8.0, ironMg = 0.0, vitaminB12Mcg = 0.1)
        )
    }
}

data class FoodResolutionResult(
    val composition: MealComposition,
    val offlineCandidates: List<MealCandidate>,
    val unresolvedSegments: List<String>,
    val needsExternalFallback: Boolean
)
