package com.example.comfood.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class UsdaFoodDataCentralService(
    private val apiKey: String
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    fun searchFoods(queries: List<String>): List<ProductInfo> {
        if (!isConfigured()) return emptyList()
        val seen = linkedSetOf<String>()
        val results = mutableListOf<ProductInfo>()
        queries.filter { it.isNotBlank() }.take(4).forEach { query ->
            fetchFoods(query).forEach { product ->
                val key = "${product.name.lowercase(Locale.US)}|${product.brand.orEmpty().lowercase(Locale.US)}"
                if (seen.add(key)) {
                    results += product
                }
            }
        }
        return results
    }

    private fun fetchFoods(query: String): List<ProductInfo> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val url = buildString {
            append("https://api.nal.usda.gov/fdc/v1/foods/search")
            append("?api_key=")
            append(encodedKey)
            append("&query=")
            append(encodedQuery)
            append("&pageSize=8")
            append("&dataType=Branded")
            append("&dataType=Foundation")
            append("&dataType=SR%20Legacy")
        }
        val root = JSONObject(fetch(url))
        val foods = root.optJSONArray("foods") ?: JSONArray()
        return buildList {
            for (index in 0 until foods.length()) {
                val item = foods.optJSONObject(index) ?: continue
                add(parseFood(item))
            }
        }.filter { it.macrosPer100g != null }
    }

    private fun parseFood(food: JSONObject): ProductInfo {
        val nutrients = food.optJSONArray("foodNutrients") ?: JSONArray()
        val calories = nutrients.findValue("208", "Energy", "Energy (Atwater General)")
        val protein = nutrients.findValue("203", "Protein")
        val carbs = nutrients.findValue("205", "Carbohydrate, by difference")
        val fat = nutrients.findValue("204", "Total lipid (fat)")

        val macros = if (calories != null && protein != null && carbs != null && fat != null) {
            MacroEstimate(
                calories = calories.roundToInt(),
                proteinGrams = protein,
                carbsGrams = carbs,
                fatGrams = fat
            )
        } else {
            null
        }

        val fdcId = food.optLong("fdcId")
        val brand = food.optString("brandOwner")
            .ifBlank { food.optString("brandName") }
            .ifBlank { null }

        return ProductInfo(
            barcode = "",
            name = food.optString("description").ifBlank { "Unnamed USDA food" },
            brand = brand,
            ingredientsText = null,
            sourceUrl = if (fdcId > 0L) {
                "https://fdc.nal.usda.gov/fdc-app.html#/food-details/$fdcId"
            } else {
                "https://fdc.nal.usda.gov/"
            },
            macrosPer100g = macros
        )
    }

    private fun JSONArray.findValue(nutrientNumber: String, vararg nutrientNames: String): Double? {
        for (index in 0 until length()) {
            val nutrient = optJSONObject(index) ?: continue
            val name = nutrient.optString("nutrientName")
            val number = nutrient.optString("nutrientNumber")
            val value = nutrient.optDouble("value").takeUnless { it.isNaN() }
            if (value != null && (number == nutrientNumber || nutrientNames.any { it == name })) {
                return value
            }
        }
        return null
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
}
