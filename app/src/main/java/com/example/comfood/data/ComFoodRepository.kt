package com.example.comfood.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ComFoodRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("comfood_prefs", Context.MODE_PRIVATE)

    fun loadEntries(): List<FoodLogEntry> {
        val raw = prefs.getString(KEY_LOGS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val macros = item.getJSONObject("macros")
                val nutrition = item.optJSONObject("nutrition")
                add(
                    FoodLogEntry(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        source = item.getString("source"),
                        timestampUtcMillis = item.getLong("timestampUtcMillis"),
                        macros = MacroEstimate(
                            calories = macros.getInt("calories"),
                            proteinGrams = macros.getDouble("proteinGrams"),
                            carbsGrams = macros.getDouble("carbsGrams"),
                            fatGrams = macros.getDouble("fatGrams")
                        ),
                        nutrition = nutrition?.toNutritionEstimate()
                    )
                )
            }
        }.sortedByDescending { it.timestampUtcMillis }
    }

    fun saveEntries(entries: List<FoodLogEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("source", entry.source)
                    put("timestampUtcMillis", entry.timestampUtcMillis)
                    put(
                        "macros",
                        JSONObject().apply {
                            put("calories", entry.macros.calories)
                            put("proteinGrams", entry.macros.proteinGrams)
                            put("carbsGrams", entry.macros.carbsGrams)
                            put("fatGrams", entry.macros.fatGrams)
                        }
                    )
                    put("nutrition", entry.nutrition?.toJson())
                }
            )
        }
        prefs.edit().putString(KEY_LOGS, array.toString()).apply()
    }

    fun loadPendingApprovals(): List<PendingMealApproval> {
        val raw = prefs.getString(KEY_PENDING_APPROVALS, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val candidates = item.optJSONArray("candidates") ?: JSONArray()
                add(
                    PendingMealApproval(
                        id = item.getString("id"),
                        originalQuery = item.getString("originalQuery"),
                        sourceDevice = item.optString("sourceDevice").ifBlank { "phone" },
                        timestampUtcMillis = item.getLong("timestampUtcMillis"),
                        candidates = candidates.toMealCandidates()
                    )
                )
            }
        }.sortedByDescending { it.timestampUtcMillis }
    }

    fun savePendingApprovals(entries: List<PendingMealApproval>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("originalQuery", entry.originalQuery)
                    put("sourceDevice", entry.sourceDevice)
                    put("timestampUtcMillis", entry.timestampUtcMillis)
                    put("candidates", entry.candidates.toJsonArray())
                }
            )
        }
        prefs.edit().putString(KEY_PENDING_APPROVALS, array.toString()).apply()
    }

    fun loadAvoidedIngredients(): Set<String> =
        prefs.getStringSet(KEY_AVOIDED_INGREDIENTS, emptySet()).orEmpty()

    fun saveAvoidedIngredients(selectedKeys: Set<String>) {
        prefs.edit().putStringSet(KEY_AVOIDED_INGREDIENTS, selectedKeys).apply()
    }

    fun registerChangeListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LOGS || key == KEY_PENDING_APPROVALS || key == KEY_AVOIDED_INGREDIENTS) {
                onChanged()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun writeExport(entries: List<FoodLogEntry>, exportDirectory: File): File {
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }
        val exportFile = File(exportDirectory, "comfood-export.json")
        val payload = JSONObject().apply {
            put("generatedAtUtcMillis", System.currentTimeMillis())
            put(
                "entries",
                JSONArray().apply {
                    entries.forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("id", entry.id)
                                put("title", entry.title)
                                put("source", entry.source)
                                put("timestampUtcMillis", entry.timestampUtcMillis)
                                put(
                                    "macros",
                                    JSONObject().apply {
                                        put("calories", entry.macros.calories)
                                        put("proteinGrams", entry.macros.proteinGrams)
                                        put("carbsGrams", entry.macros.carbsGrams)
                                        put("fatGrams", entry.macros.fatGrams)
                                    }
                                )
                                put("nutrition", entry.nutrition?.toJson())
                            }
                        )
                    }
                }
            )
            put(
                "pendingApprovals",
                JSONArray().apply {
                    loadPendingApprovals().forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("id", entry.id)
                                put("originalQuery", entry.originalQuery)
                                put("sourceDevice", entry.sourceDevice)
                                put("timestampUtcMillis", entry.timestampUtcMillis)
                                put("candidates", entry.candidates.toJsonArray())
                            }
                        )
                    }
                }
            )
        }
        exportFile.writeText(payload.toString(2))
        return exportFile
    }

    private fun JSONArray.toMealCandidates(): List<MealCandidate> =
        buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val product = item.getJSONObject("product")
                val macros = product.optJSONObject("macrosPer100g")
                val nutrition = product.optJSONObject("nutritionPerServing")
                add(
                    MealCandidate(
                        product = ProductInfo(
                            barcode = product.optString("barcode"),
                            name = product.getString("name"),
                            brand = product.optString("brand").ifBlank { null },
                            ingredientsText = product.optString("ingredientsText").ifBlank { null },
                            sourceUrl = product.getString("sourceUrl"),
                            macrosPer100g = macros?.let {
                                MacroEstimate(
                                    calories = it.getInt("calories"),
                                    proteinGrams = it.getDouble("proteinGrams"),
                                    carbsGrams = it.getDouble("carbsGrams"),
                                    fatGrams = it.getDouble("fatGrams")
                                )
                            },
                            nutritionPerServing = nutrition?.toNutritionEstimate(),
                            matchedQueryScore = product.optInt("matchedQueryScore")
                        ),
                        explanation = item.optString("explanation"),
                        confidence = item.optInt("confidence")
                    )
                )
            }
        }

    private fun List<MealCandidate>.toJsonArray(): JSONArray =
        JSONArray().apply {
            this@toJsonArray.forEach { candidate ->
                put(
                    JSONObject().apply {
                        put("explanation", candidate.explanation)
                        put("confidence", candidate.confidence)
                        put(
                            "product",
                            JSONObject().apply {
                                put("barcode", candidate.product.barcode)
                                put("name", candidate.product.name)
                                put("brand", candidate.product.brand)
                                put("ingredientsText", candidate.product.ingredientsText)
                                put("sourceUrl", candidate.product.sourceUrl)
                                put("matchedQueryScore", candidate.product.matchedQueryScore)
                                put(
                                    "macrosPer100g",
                                    candidate.product.macrosPer100g?.let { macros ->
                                        JSONObject().apply {
                                            put("calories", macros.calories)
                                            put("proteinGrams", macros.proteinGrams)
                                            put("carbsGrams", macros.carbsGrams)
                                            put("fatGrams", macros.fatGrams)
                                        }
                                    }
                                )
                                put("nutritionPerServing", candidate.product.nutritionPerServing?.toJson())
                            }
                        )
                    }
                )
            }
        }

    private fun JSONObject.toNutritionEstimate(): NutritionEstimate =
        NutritionEstimate(
            fiberGrams = optDouble("fiberGrams", 0.0),
            sugarGrams = optDouble("sugarGrams", 0.0),
            sodiumMg = optDouble("sodiumMg", 0.0),
            potassiumMg = optDouble("potassiumMg", 0.0),
            calciumMg = optDouble("calciumMg", 0.0),
            ironMg = optDouble("ironMg", 0.0),
            vitaminCMg = optDouble("vitaminCMg", 0.0),
            vitaminDMcg = optDouble("vitaminDMcg", 0.0),
            vitaminAMcg = optDouble("vitaminAMcg", 0.0),
            vitaminB12Mcg = optDouble("vitaminB12Mcg", 0.0)
        )

    private fun NutritionEstimate.toJson(): JSONObject =
        JSONObject().apply {
            put("fiberGrams", fiberGrams)
            put("sugarGrams", sugarGrams)
            put("sodiumMg", sodiumMg)
            put("potassiumMg", potassiumMg)
            put("calciumMg", calciumMg)
            put("ironMg", ironMg)
            put("vitaminCMg", vitaminCMg)
            put("vitaminDMcg", vitaminDMcg)
            put("vitaminAMcg", vitaminAMcg)
            put("vitaminB12Mcg", vitaminB12Mcg)
        }

    private companion object {
        const val KEY_LOGS = "logs"
        const val KEY_PENDING_APPROVALS = "pending_approvals"
        const val KEY_AVOIDED_INGREDIENTS = "avoided_ingredients"
    }
}
