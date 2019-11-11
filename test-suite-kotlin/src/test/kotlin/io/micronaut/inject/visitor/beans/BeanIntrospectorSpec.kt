package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BeanIntrospectorSpec {

    @Test
    fun testGetIntrospection() {
        val introspection = BeanIntrospector.SHARED.getIntrospection(TestBean::class.java)

        assertEquals(5, introspection.propertyNames.size)
        assertTrue(introspection.getProperty("age").isPresent)
        assertTrue(introspection.getProperty("name").isPresent)

        val testBean = introspection.instantiate("fred", 10, arrayOf("one"))

        assertEquals("fred", testBean.name)
        assertFalse(testBean.flag)

        try {
            introspection.getProperty("name").get().set(testBean, "bob")
            fail<Any>("Should have failed with unsupported operation, readonly")
        } catch (e: UnsupportedOperationException) {
        }

        assertEquals("default", testBean.stuff)

        introspection.getProperty("stuff").get().set(testBean, "newvalue")
        introspection.getProperty("flag").get().set(testBean, true)
        assertEquals(true, introspection.getProperty("flag", Boolean::class.java).get().get(testBean))

        assertEquals("newvalue", testBean.stuff)
    }


}
