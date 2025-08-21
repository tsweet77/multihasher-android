package com.anthroteacher.multihasher

import androidx.compose.ui.unit.dp

object AppConstants {
    const val APP_NAME = "Multihasher"
    const val VERSION = "Version 1.32 (Awesome)" // Updated version

    // Input Limits & Defaults
    const val MAX_INTENTION_LENGTH = 10000
    const val MIN_HASH_LEVELS = 1
    const val MAX_HASH_LEVELS = 1000
    const val DEFAULT_HASH_LEVELS = "1"
    const val MIN_REPS_PER_LEVEL = 1
    const val MAX_REPS_PER_LEVEL = 100000 // 100k
    const val DEFAULT_REPS_PER_LEVEL = "1"

    // Encoding Options
    const val ENCODING_64_BIT = "64-Bit"
    const val ENCODING_256_BIT = "256-Bit"
    const val ENCODING_512_BIT = "512-Bit"
    val ENCODING_OPTIONS = listOf(ENCODING_64_BIT, ENCODING_256_BIT, ENCODING_512_BIT)
    const val DEFAULT_ENCODING = ENCODING_512_BIT

    // UI Dimensions
    val SCREEN_PADDING = 16.dp
    val ITEM_SPACING = 16.dp
    val BUTTON_SPACING = 8.dp
    val INTENTION_BOX_HEIGHT = 120.dp
    val HASH_DISPLAY_PADDING = 8.dp
    val BUTTON_HEIGHT = 48.dp

    // Semantics Descriptions
    const val HASH_DISPLAY_SEMANTICS = "Hash Result Display. Click to copy."
    const val HASH_COPIED_SEMANTICS = "Hash copied to clipboard." // For Toast confirmation
}