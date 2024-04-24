package com.epimorphics.greeting

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Controller for greeting request mappings and implementation.
 */
@Controller
@RequestMapping("/greeting")
class GreetingContoller(
    // An optional multilingual greeting plugin.
    @Autowired(required = false) private val plugin: LanguagePlugin? = null
) {
    @GetMapping
    @ResponseBody
    fun getGreeting(
        @RequestParam(required = true) name: String,
        @RequestParam(required = false) language: String? = null
    ): String {
        val format = if (language != null) {
            plugin?.greetingFormat(language) ?: "Hello %s! Unfortunately, greetings in your preferred language are not available."
        } else {
            "Hello %s!"
        }

        return format.format(name)
    }
}