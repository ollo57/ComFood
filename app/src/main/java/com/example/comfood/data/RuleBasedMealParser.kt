package com.example.comfood.data

import java.util.Locale

class RuleBasedMealParser(
    private val brandProfiles: List<BrandProfile>
) {
    fun parse(query: String): ParsedMealQuery {
        val rawNormalized = query.normalizedText()
        val brand = findBrand(rawNormalized)
        val normalized = rawNormalized
            .split(" ")
            .filter { it.isNotBlank() }
            .filterNot { it in speechFillers }
            .map { token -> canonicalizeToken(token) }
            .joinToString(" ")

        return ParsedMealQuery(
            original = query,
            rawNormalized = rawNormalized,
            normalized = normalized,
            brand = brand,
            quantityHints = quantityHints.filter { it in rawNormalized }.toSet(),
            sizeHints = sizeHints.filter { it in rawNormalized }.toSet(),
            descriptorHints = descriptorHints.filter { it in rawNormalized }.toSet(),
            familyHints = familyHints.filter { it in rawNormalized }.toSet(),
            preferredCandidates = preferredCandidates(rawNormalized, brand)
        )
    }

    private fun preferredCandidates(rawNormalized: String, brand: BrandProfile?): List<String> {
        if (brand == null) return emptyList()
        val rules = preferredFamilyItems[brand.canonical].orEmpty()
        return buildList {
            rules.forEach { (family, preferredItem) ->
                if (family in rawNormalized && descriptorHints.none { it in rawNormalized }) {
                    add(preferredItem)
                }
            }
        }
    }

    private fun findBrand(query: String): BrandProfile? =
        brandProfiles.maxByOrNull { brand ->
            brand.aliases.maxOfOrNull { alias ->
                val normalizedAlias = alias.normalizedText()
                when {
                    query.contains(normalizedAlias) -> 20
                    else -> query.tokens().sumOf { queryToken ->
                        normalizedAlias.tokens().maxOfOrNull { aliasToken ->
                            tokenSimilarity(queryToken, aliasToken)
                        } ?: 0
                    }
                }
            } ?: 0
        }?.takeIf { brand ->
            brand.aliases.any { alias ->
                val normalizedAlias = alias.normalizedText()
                query.contains(normalizedAlias) ||
                    query.tokens().any { queryToken ->
                        normalizedAlias.tokens().any { aliasToken ->
                            tokenSimilarity(queryToken, aliasToken) >= 4
                        }
                    }
            }
        }

    private fun canonicalizeToken(token: String): String {
        brandProfiles.forEach { brand ->
            if (brand.aliases.any { alias ->
                    val aliasTokens = alias.normalizedText().split(" ")
                    token in aliasTokens || aliasTokens.any { aliasToken ->
                        tokenSimilarity(token, aliasToken) >= 4
                    }
                }
            ) {
                return brand.canonical
            }
        }
        return canonicalTokenMap.entries.firstOrNull { (_, aliases) ->
            token in aliases || aliases.any { alias -> tokenSimilarity(token, alias) >= 4 }
        }?.key ?: token
    }

    private fun String.tokens(): Set<String> =
        split(" ").filter { it.length > 1 }.toSet()

    private fun String.normalizedText(): String =
        normalizeFoodText()

    private fun tokenSimilarity(a: String, b: String): Int {
        if (a == b) return 8
        if (a.contains(b) || b.contains(a)) return 5
        val distance = levenshtein(a, b)
        return when (distance) {
            1 -> 5
            2 -> 4
            3 -> 2
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
                dp[j + 1] = minOf(dp[j + 1] + 1, dp[j] + 1, previous + cost)
                previous = current
            }
        }
        return dp[b.length]
    }

    companion object {
        val speechFillers = setOf("i", "ate", "had", "got", "just", "today", "for", "lunch", "dinner", "breakfast")
        val quantityHints = setOf("3 piece", "4 piece", "6 piece", "10 piece", "12 piece", "8 count")
        val sizeHints = setOf("small", "medium", "large", "junior", "jr", "little", "double", "single", "footlong", "6 inch")
        val descriptorHints = setOf("grilled", "crispy", "spicy", "deluxe", "bacon", "bbq", "cheese", "fish", "double", "single", "junior", "jr", "little", "classic", "original", "artisan", "gouda")
        val familyHints = setOf("chicken sandwich", "burger", "whopper", "crunchwrap", "quesadilla", "pizza", "nugget", "tender", "wrap", "burrito", "taco", "salad", "fries", "fish sandwich")

        val canonicalTokenMap = mapOf(
            "sandwich" to listOf("sandwich", "sammich", "sando"),
            "chicken" to listOf("chicken", "chikin", "chk"),
            "burger" to listOf("burger", "hamburger"),
            "fries" to listOf("fries", "frys"),
            "wrap" to listOf("wrap", "rap"),
            "salad" to listOf("salad"),
            "mcchicken" to listOf("mcchicken", "mc chicken", "mac chicken"),
            "crunchwrap" to listOf("crunchwrap", "crunch rap", "crunch wrap"),
            "quesadilla" to listOf("quesadilla", "quesidilla", "case of dilla"),
            "whopper" to listOf("whopper", "whopa", "wopper"),
            "filet o fish" to listOf("filet o fish", "fillet o fish", "fish sandwich")
        )

        val preferredFamilyItems = mapOf(
            "mcdonalds" to mapOf("chicken sandwich" to "mcchicken", "burger" to "big mac"),
            "burger king" to mapOf("burger" to "whopper", "chicken sandwich" to "original chicken sandwich"),
            "taco bell" to mapOf("crunchwrap" to "crunchwrap supreme"),
            "chick fil a" to mapOf("chicken sandwich" to "chick fil a chicken sandwich")
        )
    }
}

data class ParsedMealQuery(
    val original: String,
    val rawNormalized: String,
    val normalized: String,
    val brand: BrandProfile?,
    val quantityHints: Set<String>,
    val sizeHints: Set<String>,
    val descriptorHints: Set<String>,
    val familyHints: Set<String>,
    val preferredCandidates: List<String>
)
