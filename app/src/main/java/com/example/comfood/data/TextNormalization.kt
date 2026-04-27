package com.example.comfood.data

import java.text.Normalizer
import java.util.Locale

fun String.normalizeFoodText(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.US)
        .replace("&", " and ")
        .replace("'", "")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
