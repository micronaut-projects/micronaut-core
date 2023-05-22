package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyPrimitiveConfig {
    var port = 0
    var defaultValue = 9999
}
