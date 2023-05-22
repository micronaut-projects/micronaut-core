package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty("teams")
class ParentEachPropsCtor(@Parameter val name: String, val manager: ManagerProps?) {

    var wins: Int? = null

    @ConfigurationProperties("manager")
    class ManagerProps(@Parameter val name: String) {
        var age: Int? = null
    }
}
