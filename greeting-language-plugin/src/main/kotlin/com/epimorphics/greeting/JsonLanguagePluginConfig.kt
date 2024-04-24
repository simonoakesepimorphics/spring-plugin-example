package com.epimorphics.greeting

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration class which adds the [JsonLanguagePlugin] to the application context.
 * See src/main/resources/context.xml for XML configuration referencing this class.
 */
@Configuration
class JsonLanguagePluginConfig {
    private val log = LoggerFactory.getLogger(JsonLanguagePluginConfig::class.java)

    @Bean
    fun mapper(): ObjectMapper {
        return ObjectMapper().registerKotlinModule()
    }

    @Bean
    fun plugin(mapper: ObjectMapper): LanguagePlugin {
        log.info("Configuring JSON language plugin.")
        return JsonLanguagePlugin(mapper)
    }
}