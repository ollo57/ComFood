package com.example.comfood.data

class HeuristicAiFallback {
    fun rewriteQueries(parsed: ParsedMealQuery): List<String> {
        val variants = linkedSetOf<String>()
        variants += parsed.normalized
        variants += parsed.preferredCandidates

        parsed.brand?.let { brand ->
            val withoutBrand = parsed.normalized.replace(brand.canonical, "").trim()
            if (withoutBrand.isNotBlank()) {
                variants += "${brand.canonical} $withoutBrand"
                variants += withoutBrand
            }
        }

        if ("chicken sandwich" in parsed.familyHints && parsed.brand?.canonical == "mcdonalds") {
            variants += "mcdonalds mcchicken"
        }
        if ("crunchwrap" in parsed.familyHints && parsed.brand?.canonical == "taco bell") {
            variants += "taco bell crunchwrap supreme"
        }
        if ("burger" in parsed.familyHints && parsed.brand?.canonical == "burger king") {
            variants += "burger king whopper"
        }

        return variants.filter { it.isNotBlank() }
    }

    fun clarificationMessage(result: LocalMenuMatchResult.Ambiguous): String {
        val brand = result.parsed.brand?.canonical?.replaceFirstChar { it.uppercase() } ?: "that restaurant"
        val names = result.candidates.take(3).joinToString(", ") { it.name }
        return "I found multiple likely $brand items: $names. Add a detail like grilled, crispy, spicy, count, or size."
    }
}
