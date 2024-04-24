package com.epimorphics.greeting

/**
 * An optional plugin which provides a format string for a greeting in a specific language.
 */
interface LanguagePlugin {
    fun greetingFormat(language: String): String?
    fun languages(): Set<String>
}