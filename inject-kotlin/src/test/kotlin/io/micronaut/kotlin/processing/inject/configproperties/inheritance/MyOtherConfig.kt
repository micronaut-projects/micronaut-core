package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.baz")
class MyOtherConfig: ParentPojo() {

    var onlySetter: String? = null
    var otherProperty: String? = null

}
