package io.micronaut.kotlin.processing.beans.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(value = "teams")
open class AbstractConfigImpl @ConfigurationInject constructor(
    @Parameter("name") val name: String,
    value: String
) : AbstractConfig(value = value) {

    var childThing: String? = "def childThing"
    override var thing: String = "thing"
}
