package io.micronaut.kotlin.processing.beans.configproperties.inheritance

abstract class AbstractConfig(
    val value: String = ""
) {
    var notThing: String = "def notThing"
    abstract val thing: String
}
