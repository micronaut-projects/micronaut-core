package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("baz")
class ChildConfig: MyConfig() {
    var stuff: String? = null
}
