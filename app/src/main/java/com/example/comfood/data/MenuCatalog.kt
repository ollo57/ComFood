package com.example.comfood.data

import java.util.Locale

class MenuCatalog(menuItems: List<LocalMenuItem>) {
    private val indexedItems = menuItems.map { item ->
        IndexedMenuItem(
            item = item,
            familyTags = inferTags(item, RuleBasedMealParser.familyHints),
            descriptorTags = inferTags(item, RuleBasedMealParser.descriptorHints),
            sizeTags = inferTags(item, RuleBasedMealParser.sizeHints),
            quantityTags = inferTags(item, RuleBasedMealParser.quantityHints)
        )
    }

    private val byRestaurant = indexedItems.groupBy { it.item.restaurant }

    fun candidatesFor(parsed: ParsedMealQuery): List<IndexedMenuItem> {
        var candidates = if (parsed.brand != null) {
            byRestaurant[parsed.brand.canonical].orEmpty()
        } else {
            indexedItems
        }

        if (parsed.preferredCandidates.isNotEmpty()) {
            val preferred = candidates.filter { indexed ->
                parsed.preferredCandidates.any { preferredText ->
                    preferredText in indexed.item.name.normalizedText() ||
                        indexed.item.aliases.any { preferredText in it.normalizedText() }
                }
            }
            if (preferred.isNotEmpty()) {
                candidates = preferred
            }
        }

        if (parsed.familyHints.isNotEmpty()) {
            val familyFiltered = candidates.filter { indexed ->
                indexed.familyTags.any { it in parsed.familyHints }
            }
            if (familyFiltered.isNotEmpty()) {
                candidates = familyFiltered
            }
        }

        if (parsed.descriptorHints.isNotEmpty()) {
            val descriptorFiltered = candidates.filter { indexed ->
                parsed.descriptorHints.any { it in indexed.descriptorTags }
            }
            if (descriptorFiltered.isNotEmpty()) {
                candidates = descriptorFiltered
            }
        }

        if (parsed.sizeHints.isNotEmpty()) {
            val sizeFiltered = candidates.filter { indexed ->
                parsed.sizeHints.any { it in indexed.sizeTags }
            }
            if (sizeFiltered.isNotEmpty()) {
                candidates = sizeFiltered
            }
        }

        if (parsed.quantityHints.isNotEmpty()) {
            val quantityFiltered = candidates.filter { indexed ->
                parsed.quantityHints.any { it in indexed.quantityTags }
            }
            if (quantityFiltered.isNotEmpty()) {
                candidates = quantityFiltered
            }
        }

        return candidates
    }

    private fun inferTags(item: LocalMenuItem, tagSet: Set<String>): Set<String> {
        val haystack = buildString {
            append(item.name.normalizedText())
            append(' ')
            append(item.aliases.joinToString(" ") { it.normalizedText() })
        }
        return tagSet.filter { tag -> tag in haystack }.toSet()
    }

    private fun String.normalizedText(): String =
        normalizeFoodText()
}

data class IndexedMenuItem(
    val item: LocalMenuItem,
    val familyTags: Set<String>,
    val descriptorTags: Set<String>,
    val sizeTags: Set<String>,
    val quantityTags: Set<String>
)
