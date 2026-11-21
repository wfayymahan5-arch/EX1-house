package com.ex1legend.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeRoomCategoriesTest {
    @Test fun mapsKnownGameCategories() {
        assertEquals("battle_royale", normalizeGameCategory("battle_royale"))
        assertEquals("fight", normalizeGameCategory("fight"))
        assertEquals("multiplayer", normalizeGameCategory("multiplayer"))
    }

    @Test fun sendsMissingAndUnknownGameCategoriesToLegacyBucket() {
        assertEquals("uncategorized", normalizeGameCategory(null))
        assertEquals("uncategorized", normalizeGameCategory(""))
        assertEquals("uncategorized", normalizeGameCategory("future_mode"))
    }

    @Test fun mapsKnownPriceCategories() {
        assertEquals("free", normalizePriceCategory("free"))
        assertEquals("economy", normalizePriceCategory("economy"))
        assertEquals("expensive", normalizePriceCategory("expensive"))
    }

    @Test fun sendsMissingAndUnknownPriceCategoriesToLegacyBucket() {
        assertEquals("uncategorized", normalizePriceCategory(null))
        assertEquals("uncategorized", normalizePriceCategory(""))
        assertEquals("uncategorized", normalizePriceCategory("premium_unknown"))
    }

    @Test fun trimsAndNormalizesCategoryCase() {
        assertEquals("fight", normalizeGameCategory(" FIGHT "))
        assertEquals("economy", normalizePriceCategory(" Economy "))
    }
}
