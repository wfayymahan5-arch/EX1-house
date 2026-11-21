package com.ex1legend.app

internal fun normalizeGameCategory(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "battle_royale" -> "battle_royale"
    "fight" -> "fight"
    "multiplayer" -> "multiplayer"
    else -> "uncategorized"
}

internal fun normalizePriceCategory(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "free" -> "free"
    "economy" -> "economy"
    "expensive" -> "expensive"
    else -> "uncategorized"
}
