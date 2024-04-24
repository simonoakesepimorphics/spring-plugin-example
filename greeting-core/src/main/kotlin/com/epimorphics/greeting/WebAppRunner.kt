package com.epimorphics.greeting

import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

/**
 * Main Spring application class for the core application.
 * External plugins add additional features - see [ExternalConfig] for plugin configuration.
 */
@SpringBootApplication
@ComponentScan(useDefaultFilters = false)
@Import(
    ExternalConfig::class,
    GreetingContoller::class
)
class WebAppRunner

fun main(args: Array<String>) {
    SpringApplicationBuilder(WebAppRunner::class.java)
        .bannerMode(Banner.Mode.OFF)
        .build().run(*args)
}