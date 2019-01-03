package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import junit.framework.TestCase

class MapFormatSpec: TestCase() {

    fun testMapFormatOnProperty() {
        val context = ApplicationContext.run(mapOf("text.properties.yyy.zzz" to 3, "test.properties.yyy.xxx" to 2, "test.properties.yyy.yyy" to 3))
        val config = context.getBean(ConfigProps::class.java)
        TestCase.assertEquals(config.properties?.get("yyy.xxx"), 2)
    }
}
