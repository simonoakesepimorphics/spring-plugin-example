package com.epimorphics.greeting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * [LanguagePlugin] implementation which obtains messages from a packaged JSON resource.
 */
class JsonLanguagePlugin(
    private val mapper: ObjectMapper
): LanguagePlugin {
    private val greetings by lazy {
        javaClass.getResourceAsStream("/greeting.json")!!.use { input ->
            mapper.readValue<Map<String, String>>(input)
        }
    }

    override fun greetingFormat(language: String): String? {
        return greetings[language]
    }

    override fun languages(): Set<String> {
        return greetings.keys
    }
}