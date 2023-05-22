package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty

@EachProperty("teams")
class ParentEachProps {

    var wins: Int? = null
    var manager: ManagerProps? = null

    @ConfigurationProperties("manager")
    class ManagerProps {
        var age: Int? = null
    }
}
