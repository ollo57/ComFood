package com.example.comfood.data

import java.util.Locale
import kotlin.math.max

class LocalMenuMatcher(
    private val brandProfiles: List<BrandProfile>,
    private val menuItems: List<LocalMenuItem>
) {
    private val parser = RuleBasedMealParser(brandProfiles)
    private val catalog = MenuCatalog(menuItems)

    fun matchMeal(query: String): LocalMenuItem? =
        when (val result = matchDetailed(query)) {
            is LocalMenuMatchResult.Exact -> result.item
            else -> null
        }

    fun matchDetailed(query: String): LocalMenuMatchResult {
        val parsed = parser.parse(query)
        val candidates = catalog.candidatesFor(parsed)
            .asSequence()
            .map { indexed -> indexed.item to score(indexed.item, parsed) }
            .sortedByDescending { it.second }
            .toList()

        val threshold = if (parsed.brand != null) 10 else 16
        val best = candidates.firstOrNull { (_, score) -> score >= threshold } ?: return LocalMenuMatchResult.NoMatch(parsed)
        val top = candidates.take(3)
        val ambiguous = top.size >= 2 &&
            top[0].second - top[1].second <= 4 &&
            parsed.brand != null &&
            parsed.familyHints.isNotEmpty() &&
            parsed.descriptorHints.isEmpty()

        return if (ambiguous) {
            LocalMenuMatchResult.Ambiguous(parsed, top.map { it.first })
        } else {
            LocalMenuMatchResult.Exact(parsed, best.first)
        }
    }

    fun normalizeMealQuery(query: String): String = parser.parse(query).normalized

    private fun score(item: LocalMenuItem, parsed: ParsedMealQuery): Int {
        val queryTokens = parsed.normalized.tokens()
        val itemTokens = buildSet {
            addAll(item.name.normalizedText().tokens())
            item.aliases.forEach { addAll(it.normalizedText().tokens()) }
        }
        val exactOverlap = queryTokens.count { it in itemTokens } * 7
        val fuzzyOverlap = queryTokens.sumOf { token ->
            itemTokens.maxOfOrNull { itemToken -> tokenSimilarity(token, itemToken) } ?: 0
        }
        val aliasBonus = if (item.aliases.any { parsed.rawNormalized.contains(it.normalizedText()) }) 14 else 0
        val brandBonus = if (parsed.brand != null && item.restaurant == parsed.brand.canonical) 8 else 0
        val itemPhraseBonus = if (parsed.rawNormalized.contains(item.name.normalizedText())) 16 else 0
        val collapsedPhraseBonus = collapsedPhraseBonus(item, parsed.rawNormalized)
        val familyBonus = familyBonus(item, parsed)
        val preferredBonus = preferredBonus(item, parsed)
        val preferredPenalty = if (
            parsed.preferredCandidates.isNotEmpty() &&
            preferredBonus == 0 &&
            parsed.descriptorHints.isEmpty()
        ) {
            -18
        } else {
            0
        }
        val sizeBonus = hintBonus(item, parsed.sizeHints, 10)
        val quantityBonus = hintBonus(item, parsed.quantityHints, 12)
        val descriptorBonus = descriptorBonus(item, parsed.descriptorHints)
        return exactOverlap + fuzzyOverlap + aliasBonus + brandBonus + itemPhraseBonus +
            collapsedPhraseBonus + familyBonus + preferredBonus + preferredPenalty +
            sizeBonus + quantityBonus + descriptorBonus
    }

    private fun collapsedPhraseBonus(item: LocalMenuItem, rawNormalized: String): Int {
        val collapsedQuery = rawNormalized.replace(" ", "")
        val collapsedName = item.name.normalizedText().replace(" ", "")
        return when {
            collapsedQuery.contains(collapsedName) -> 14
            item.aliases.any { collapsedQuery.contains(it.normalizedText().replace(" ", "")) } -> 12
            else -> 0
        }
    }

    private fun familyBonus(item: LocalMenuItem, parsed: ParsedMealQuery): Int {
        val name = item.name.normalizedText()
        return parsed.familyHints.sumOf { family ->
            if (family in name || item.aliases.any { family in it.normalizedText() }) 10 else 0
        }
    }

    private fun preferredBonus(item: LocalMenuItem, parsed: ParsedMealQuery): Int =
        parsed.preferredCandidates.maxOfOrNull { preferred ->
            if (preferred in item.name.normalizedText() || item.aliases.any { preferred in it.normalizedText() }) 24 else 0
        } ?: 0

    private fun hintBonus(item: LocalMenuItem, hints: Set<String>, exactBonus: Int): Int {
        if (hints.isEmpty()) return 0
        val itemText = buildString {
            append(item.name.normalizedText())
            append(' ')
            append(item.aliases.joinToString(" ") { it.normalizedText() })
        }
        return hints.sumOf { hint ->
            when {
                hint in itemText -> exactBonus
                hint == "10 piece" && "10 pc" in itemText -> exactBonus
                hint == "6 piece" && "6 pc" in itemText -> exactBonus
                hint == "4 piece" && "4 pc" in itemText -> exactBonus
                hint == "3 piece" && "3 pc" in itemText -> exactBonus
                hint == "8 count" && "8 ct" in itemText -> exactBonus
                hint == "small" && "jr" in itemText -> exactBonus - 2
                else -> 0
            }
        }
    }

    private fun descriptorBonus(item: LocalMenuItem, descriptors: Set<String>): Int {
        if (descriptors.isEmpty()) return 0
        val itemText = buildString {
            append(item.name.normalizedText())
            append(' ')
            append(item.aliases.joinToString(" ") { it.normalizedText() })
        }
        return descriptors.sumOf { descriptor ->
            if (descriptor in itemText) 10 else -6
        }
    }

    private fun String.tokens(): Set<String> =
        normalizedText().split(" ").filter { it.length > 1 }.toSet()

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
}
