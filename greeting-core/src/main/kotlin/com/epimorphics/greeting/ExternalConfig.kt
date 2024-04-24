package com.epimorphics.greeting

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportResource

/**
 * Configuration hook for external plugins.
 * External beans must be defined in a file, which is located by the greeting.config.external.location application property.
 * The file location must be written in Spring resource syntax, ie. using file: and classpath: prefixes.
 */
@Configuration
@ConditionalOnProperty("greeting.config.external.location")
@ImportResource("\${greeting.config.external.location}")
class ExternalConfig