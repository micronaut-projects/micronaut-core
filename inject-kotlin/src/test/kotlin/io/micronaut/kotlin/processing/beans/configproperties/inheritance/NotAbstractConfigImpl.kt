package io.micronaut.kotlin.processing.beans.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(value = "teams")
open class NotAbstractConfigImpl @ConfigurationInject constructor(
    @Parameter("name") val name: String,
    value: String,
    val thing: String = "thing"
) : NotAbstractConfig(value = value) {

    var childThing: String? = "def childThing"
    override var superValueWithOverride:String? = "my defaultValue"
}
