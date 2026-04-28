package com.example.comfood

import com.example.comfood.data.FoodResolutionEngine
import com.example.comfood.data.IngredientRule
import com.example.comfood.data.IngredientSection
import com.example.comfood.data.LocalMenuItem
import com.example.comfood.data.MacroEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodResolutionEngineTest {
    private val items = listOf(
        LocalMenuItem("generic", "Bread", listOf("bread"), MacroEstimate(80, 3.0, 15.0, 1.0)),
        LocalMenuItem("generic", "Eggs", listOf("egg", "eggs"), MacroEstimate(72, 6.3, 0.4, 4.8)),
        LocalMenuItem("generic", "Rice", listOf("rice"), MacroEstimate(206, 4.3, 45.0, 0.4)),
        LocalMenuItem("generic", "Chicken", listOf("chicken"), MacroEstimate(165, 31.0, 0.0, 3.6)),
        LocalMenuItem("generic", "Cheese", listOf("cheese", "cheddar cheese"), MacroEstimate(455, 25.0, 3.0, 37.0)),
        LocalMenuItem("generic", "Beef", listOf("beef"), MacroEstimate(213, 26.0, 0.0, 12.0)),
        LocalMenuItem("generic", "Potato", listOf("potato", "potatoes"), MacroEstimate(161, 4.3, 37.0, 0.2)),
        LocalMenuItem("generic", "Pasta", listOf("pasta"), MacroEstimate(221, 8.0, 43.0, 1.3)),
        LocalMenuItem("generic", "Milk", listOf("milk"), MacroEstimate(122, 8.0, 12.0, 5.0)),
        LocalMenuItem("generic", "Butter", listOf("butter"), MacroEstimate(102, 0.1, 0.0, 11.5)),
        LocalMenuItem("generic", "Banana", listOf("banana"), MacroEstimate(105, 1.3, 27.0, 0.3)),
        LocalMenuItem("generic", "French Fries", listOf("fries", "french fries"), MacroEstimate(312, 3.4, 41.0, 15.0)),
        LocalMenuItem("generic", "Chicken Sandwich", listOf("chicken sandwich"), MacroEstimate(430, 24.0, 41.0, 18.0)),
        LocalMenuItem("generic", "Burger", listOf("burger"), MacroEstimate(354, 17.0, 29.0, 20.0)),
        LocalMenuItem("generic", "Pizza", listOf("pizza"), MacroEstimate(285, 12.0, 36.0, 10.0)),
        LocalMenuItem("generic", "Salad", listOf("salad"), MacroEstimate(80, 2.0, 10.0, 3.0)),

        LocalMenuItem("restaurant", "Garlic Bread Slice", listOf("garlic bread"), MacroEstimate(140, 4.0, 20.0, 5.0)),
        LocalMenuItem("restaurant", "Eggs Benedict", listOf("eggs benedict"), MacroEstimate(728, 31.0, 30.0, 51.0)),
        LocalMenuItem("restaurant", "Fried Rice", listOf("fried rice"), MacroEstimate(333, 9.0, 45.0, 12.0)),
        LocalMenuItem("restaurant", "Chicken Parmesan", listOf("chicken parmesan"), MacroEstimate(525, 45.0, 20.0, 28.0)),
        LocalMenuItem("restaurant", "Mac and Cheese", listOf("mac and cheese"), MacroEstimate(450, 15.0, 43.0, 24.0)),
        LocalMenuItem("restaurant", "Quesadilla", listOf("quesadilla"), MacroEstimate(529, 20.0, 41.0, 32.0)),
        LocalMenuItem("restaurant", "Chicken Nuggets", listOf("chicken nuggets"), MacroEstimate(296, 15.0, 18.0, 18.0))
    )

    private val rules = listOf(
        IngredientRule("milk", "Milk", "allergen", listOf("milk", "cheese", "cheddar cheese"), IngredientSection.Allergens),
        IngredientRule("egg", "Egg", "allergen", listOf("egg", "eggs"), IngredientSection.Allergens),
        IngredientRule("wheat", "Wheat", "allergen", listOf("bread", "toast"), IngredientSection.Allergens),
        IngredientRule("soy", "Soy", "allergen", listOf("soy", "soy sauce"), IngredientSection.Allergens)
    )

    private val engine = FoodResolutionEngine(localMenuItems = items, ingredientRules = rules)

    @Test
    fun genericBaseFoodsStayGeneric() {
        val inputs = listOf("bread", "eggs", "rice", "chicken", "cheese", "beef", "potato", "pasta", "milk", "butter")
        val forbidden = setOf("Garlic Bread Slice", "Eggs Benedict", "Fried Rice", "Chicken Parmesan", "Mac and Cheese", "Quesadilla", "Chicken Nuggets")

        inputs.forEach { input ->
            val result = engine.resolveOffline(input)
            assertTrue("No food resolved for $input", result.composition.foods.isNotEmpty())
            assertFalse(
                "Input $input should not expand to specific dish",
                result.composition.foods.any { it.label in forbidden }
            )
        }
    }

    @Test
    fun nonExpansionRuleBlocksOverSpecificMappings() {
        assertFoodLabel("bread", "Bread")
        assertFoodLabel("rice", "Rice")
        assertFoodLabel("eggs", "Eggs")
        assertFoodLabel("cheese", "Cheese")
        assertFoodLabel("chicken", "Chicken")
        assertFoodLabel("beef", "Beef")
    }

    @Test
    fun decomposesAmbiguousMealsWithoutBrandInference() {
        val eggsCheese = engine.resolveOffline("eggs with cheese")
        assertTrue(eggsCheese.composition.foods.any { it.label == "Eggs" })
        assertTrue(eggsCheese.composition.ingredients.any { it.label.contains("Cheese", ignoreCase = true) })

        val chickenRice = engine.resolveOffline("chicken with rice")
        assertTrue(chickenRice.composition.foods.any { it.label == "Chicken" })
        assertTrue(chickenRice.composition.foods.any { it.label == "Rice" })

        val beefPotatoes = engine.resolveOffline("beef and potatoes")
        assertTrue(beefPotatoes.composition.foods.any { it.label == "Beef" })
        assertTrue(beefPotatoes.composition.foods.any { it.label == "Potato" })

        assertFalse(chickenRice.composition.foods.any { it.brand != null })
    }

    @Test
    fun handlesQuantityEdgeCases() {
        val oneCupCheese = engine.resolveOffline("one cup of cheese")
        val cheese = oneCupCheese.composition.foods.firstOrNull { it.label == "Cheese" }
        assertNotNull(cheese)
        assertEquals("1 cup", cheese?.quantityText)

        val halfBananaThreeEggs = engine.resolveOffline("half a banana and 3 eggs")
        assertTrue(halfBananaThreeEggs.composition.foods.any { it.label == "Banana" && it.quantityMultiplier <= 0.5 })
        assertTrue(halfBananaThreeEggs.composition.foods.any { it.label == "Eggs" && it.quantityMultiplier >= 3.0 })

        val doubleFries = engine.resolveOffline("double portion of fries")
        assertTrue(doubleFries.composition.foods.any { it.label == "French Fries" && it.quantityMultiplier >= 2.0 })

        val breadSlices = engine.resolveOffline("2.5 slices of bread")
        assertTrue(
            "Bread slices resolution was ${breadSlices.composition.foods}",
            breadSlices.composition.foods.any { it.label == "Bread" && it.quantityMultiplier >= 2.5 }
        )
    }

    @Test
    fun ingredientExtractionAndLinkingWorks() {
        val sandwich = engine.resolveOffline("chicken sandwich with lettuce and mayo")
        val parent = sandwich.composition.foods.firstOrNull { it.label == "Chicken Sandwich" }
        assertNotNull(parent)
        assertTrue(parent!!.ingredients.any { it.label.equals("Lettuce", ignoreCase = true) })
        assertTrue(parent.ingredients.any { it.label.equals("Mayo", ignoreCase = true) })

        val burger = engine.resolveOffline("burger with cheese, pickles and ketchup")
        val burgerFood = burger.composition.foods.firstOrNull { it.label == "Burger" }
        assertNotNull(burgerFood)
        assertTrue(burgerFood!!.ingredients.any { it.label.contains("Cheese", ignoreCase = true) })
        assertTrue(
            "Burger ingredients were ${burgerFood.ingredients}",
            burgerFood.ingredients.any { it.label.contains("Pickle", ignoreCase = true) }
        )
        assertTrue(burgerFood.ingredients.any { it.label.contains("Ketchup", ignoreCase = true) })
    }

    @Test
    fun explicitDishNameCanMatchSpecificDish() {
        val result = engine.resolveOffline("fried rice")
        assertTrue(result.composition.foods.any { it.label == "Fried Rice" || it.label == "Rice" })
    }

    @Test
    fun additionalQuantityScenariosAreStable() {
        val littleRiceChicken = engine.resolveOffline("a little rice and some chicken")
        assertTrue(littleRiceChicken.composition.foods.any { it.label == "Rice" && it.quantityMultiplier <= 0.5 })
        assertTrue(littleRiceChicken.composition.foods.any { it.label == "Chicken" })

        val handfulNuts = engine.resolveOffline("a handful of nuts")
        assertTrue(handfulNuts.composition.foods.any { it.label == "Nuts" })

        val friedEggsCheddar = engine.resolveOffline("fried eggs with cheddar cheese")
        val eggs = friedEggsCheddar.composition.foods.firstOrNull { it.label == "Eggs" }
        assertNotNull(eggs)
        assertTrue(eggs!!.quantityText.orEmpty().contains("fried", ignoreCase = true))
        assertTrue(friedEggsCheddar.composition.ingredients.any { it.label.contains("Cheese", ignoreCase = true) })
    }

    @Test
    fun ingredientLinkingForComplexExamples() {
        val pizza = engine.resolveOffline("pizza with extra cheese and olives")
        val pizzaFood = pizza.composition.foods.firstOrNull { it.label == "Pizza" }
        assertNotNull(pizzaFood)
        assertTrue(pizzaFood!!.ingredients.any { it.label.contains("Cheese", ignoreCase = true) })
        assertTrue(pizzaFood.ingredients.any { it.label.contains("Olive", ignoreCase = true) })

        val salad = engine.resolveOffline("salad with chicken, croutons, dressing")
        val saladFood = salad.composition.foods.firstOrNull { it.label == "Salad" }
        assertNotNull(saladFood)
        assertTrue(saladFood!!.ingredients.any { it.label.contains("Dressing", ignoreCase = true) })
    }

    private fun assertFoodLabel(input: String, expectedLabel: String) {
        val result = engine.resolveOffline(input)
        val first = result.composition.foods.firstOrNull()
        assertNotNull("Expected a resolved food for $input", first)
        assertEquals(expectedLabel, first?.label)
    }
}
