package io.micronaut.docs.config.properties

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("testing")
data class TestProperties1 @ConfigurationInject constructor(
    val enabled: Boolean? = true
)
