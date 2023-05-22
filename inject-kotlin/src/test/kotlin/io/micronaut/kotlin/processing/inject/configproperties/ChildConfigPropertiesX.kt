package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.kotlin.processing.inject.configproperties.other.ParentConfigProperties

@ConfigurationProperties("child")
class ChildConfigPropertiesX: ParentConfigProperties() {

    var age: Int? = null
}
