package com.example.comfood

import com.example.comfood.data.BrandProfile
import com.example.comfood.data.LocalMenuItem
import com.example.comfood.data.LocalMenuMatcher
import com.example.comfood.data.MacroEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalMenuMatcherTest {
    private val brands = listOf(
        BrandProfile(
            canonical = "mcdonalds",
            aliases = listOf("mcdonalds", "mcdoanlds", "mcd"),
            menuHints = listOf("mcchicken", "big mac")
        ),
        BrandProfile(
            canonical = "chick fil a",
            aliases = listOf("chick fil a", "chickfila", "chick fil"),
            menuHints = listOf("chicken sandwich", "nuggets")
        ),
        BrandProfile(
            canonical = "burger king",
            aliases = listOf("burger king", "bk"),
            menuHints = listOf("whopper", "original chicken sandwich")
        ),
        BrandProfile(
            canonical = "taco bell",
            aliases = listOf("taco bell"),
            menuHints = listOf("crunchwrap supreme", "chicken quesadilla")
        )
    )

    private val items = listOf(
        LocalMenuItem(
            restaurant = "mcdonalds",
            name = "McChicken",
            aliases = listOf("mcchicken", "mcdonalds chicken sandwich", "chicken sandwich from mcdonalds"),
            macros = MacroEstimate(400, 14.0, 39.0, 21.0)
        ),
        LocalMenuItem(
            restaurant = "mcdonalds",
            name = "Artisan Grilled Chicken Sandwich",
            aliases = listOf("artisan grilled chicken sandwich", "grilled chicken sandwich from mcdonalds"),
            macros = MacroEstimate(380, 37.0, 44.0, 7.0)
        ),
        LocalMenuItem(
            restaurant = "chick fil a",
            name = "Chick-fil-A Chicken Sandwich",
            aliases = listOf("chick fil a chicken sandwich", "chicken sandwich from chick fil a"),
            macros = MacroEstimate(420, 29.0, 41.0, 18.0)
        ),
        LocalMenuItem(
            restaurant = "burger king",
            name = "Whopper",
            aliases = listOf("whopper", "burger king whopper"),
            macros = MacroEstimate(670, 31.0, 54.0, 39.0)
        ),
        LocalMenuItem(
            restaurant = "taco bell",
            name = "Crunchwrap Supreme",
            aliases = listOf("crunchwrap supreme", "taco bell crunchwrap"),
            macros = MacroEstimate(530, 16.0, 71.0, 21.0)
        )
    )

    private val matcher = LocalMenuMatcher(brands, items)

    @Test
    fun matchesMisspelledMcDonaldsChickenSandwich() {
        val match = matcher.matchMeal("I ate a mcdoanlds chicken sandwich")
        assertNotNull(match)
        assertEquals("McChicken", match?.name)
    }

    @Test
    fun matchesChickFilAChickenSandwich() {
        val match = matcher.matchMeal("chicken sandwich from chick fil a")
        assertNotNull(match)
        assertEquals("Chick-fil-A Chicken Sandwich", match?.name)
    }

    @Test
    fun prefersMcChickenForGenericMcDonaldsChickenSandwich() {
        val match = matcher.matchMeal("mcdonalds chicken sandwich")
        assertNotNull(match)
        assertEquals("McChicken", match?.name)
    }

    @Test
    fun prefersMcChickenForApostropheMcDonaldsChickenSandwich() {
        val match = matcher.matchMeal("mcdonald's chicken sandwich")
        assertNotNull(match)
        assertEquals("McChicken", match?.name)
    }

    @Test
    fun keepsGrilledWhenUserActuallySaysGrilled() {
        val match = matcher.matchMeal("mcdonalds grilled chicken sandwich")
        assertNotNull(match)
        assertEquals("Artisan Grilled Chicken Sandwich", match?.name)
    }

    @Test
    fun matchesBurgerKingWhopper() {
        val match = matcher.matchMeal("burger king whopper")
        assertNotNull(match)
        assertEquals("Whopper", match?.name)
    }

    @Test
    fun matchesTacoBellCrunchwrap() {
        val match = matcher.matchMeal("taco bell crunchwrap")
        assertNotNull(match)
        assertEquals("Crunchwrap Supreme", match?.name)
    }

    @Test
    fun matchesMisheardMcChickenSpacing() {
        val match = matcher.matchMeal("mcdonalds mc chicken")
        assertNotNull(match)
        assertEquals("McChicken", match?.name)
    }

    @Test
    fun matchesMisheardCrunchWrapSpacing() {
        val match = matcher.matchMeal("taco bell crunch wrap")
        assertNotNull(match)
        assertEquals("Crunchwrap Supreme", match?.name)
    }
}
