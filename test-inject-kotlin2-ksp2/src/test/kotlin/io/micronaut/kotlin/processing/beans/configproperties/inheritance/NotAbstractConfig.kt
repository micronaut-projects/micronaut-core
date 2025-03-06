package io.micronaut.kotlin.processing.beans.configproperties.inheritance

open class NotAbstractConfig(
    val value: String = "",
) {
    var superValue: String? = ""
    open var superValueWithOverride: String? = "this is "
}
