package com.example.comfood.data

import android.content.Context
import org.json.JSONArray

class FoodKnowledgeLoader(private val context: Context) {
    fun loadBrandProfiles(): List<BrandProfile> =
        readArray("brand_aliases.json").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        BrandProfile(
                            canonical = item.getString("canonical"),
                            aliases = item.getJSONArray("aliases").toStringList(),
                            menuHints = item.getJSONArray("menuHints").toStringList()
                        )
                    )
                }
            }
        }

    fun loadIngredientRules(): List<IngredientRule> =
        readArray("ingredient_rules.json").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        IngredientRule(
                            key = item.getString("key"),
                            label = item.getString("label"),
                            reason = item.getString("reason"),
                            aliases = item.getJSONArray("aliases").toStringList(),
                            section = IngredientSection.valueOf(item.getString("section")),
                            isCommon = item.optBoolean("isCommon", false)
                        )
                    )
                }
            }
        }

    fun loadLocalMenuItems(): List<LocalMenuItem> =
        sequenceOf(
            readArray("restaurant_menu_items.json"),
            readArray("generic_food_expansion.json")
        ).flatMap { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val macros = item.getJSONObject("macros")
                    add(
                        LocalMenuItem(
                            restaurant = item.getString("restaurant"),
                            name = item.getString("name"),
                            aliases = item.getJSONArray("aliases").toStringList(),
                            macros = MacroEstimate(
                                calories = macros.getInt("calories"),
                                proteinGrams = macros.getDouble("protein"),
                                carbsGrams = macros.getDouble("carbs"),
                                fatGrams = macros.getDouble("fat")
                            )
                        )
                    )
                }
            }
        }.toList()

    private fun readArray(assetName: String): JSONArray =
        context.assets.open(assetName).bufferedReader().use { JSONArray(it.readText()) }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
}
