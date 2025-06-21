package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
open class MyConfig {
    var port: Int = 0
    var host: String? = null
}
