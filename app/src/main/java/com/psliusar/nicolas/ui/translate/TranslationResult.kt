package com.psliusar.nicolas.ui.translate

/**
 * Holds a result of translation operation.
 */
sealed class TranslationResult {

    data class Success(var result: String?) : TranslationResult()

    data class Error(var error: Exception?) : TranslationResult()
}