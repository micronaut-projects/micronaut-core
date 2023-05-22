package io.micronaut.kotlin.processing.inject.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

@EachProperty(value = "teams", list = true)
class ParentArrayEachPropsCtor(@Parameter private val index: Int, val manager: ManagerProps?): Ordered {

    var wins: Int? = null

    override fun getOrder() = index

    @ConfigurationProperties("manager")
    class ManagerProps(@Parameter private val index: Int): Ordered {

        var age: Int? = null

        override fun getOrder() = index
    }
}
