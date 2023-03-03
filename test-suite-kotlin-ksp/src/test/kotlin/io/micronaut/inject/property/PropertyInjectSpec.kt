package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PropertyInjectSpec {

    @Test
    fun testPropertyInjection() {
        val context = ApplicationContext.run(mapOf("app.string" to "Hello", "app.map.yyy.xxx" to 2, "app.map.yyy.yyy" to 3))
        val config = context.getBean(BeanWithProperty::class.java)
        Assertions.assertEquals(config.stringParam, "Hello")
        Assertions.assertEquals(config.mapParam?.get("yyy.xxx"), "2")
        Assertions.assertEquals(config.mapParam?.get("yyy.yyy"), "3")
        context.close()
    }
}
