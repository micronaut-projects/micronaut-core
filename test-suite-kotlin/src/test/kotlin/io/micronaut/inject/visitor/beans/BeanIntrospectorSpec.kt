package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospector
import junit.framework.TestCase

class BeanIntrospectorSpec : TestCase() {

    fun testGetIntrospection() {
        val introspection = BeanIntrospector.SHARED.getIntrospection(TestBean::class.java)

        assertEquals(4, introspection.propertyNames.size)
        assertTrue(introspection.getProperty("age").isPresent)
        assertTrue(introspection.getProperty("name").isPresent)

        val testBean = introspection.instantiate("fred", 10, arrayOf("one"))

        assertEquals("fred", testBean.name)

        try {
            introspection.getProperty("name").get().write(testBean, "bob")
            fail("Should have failed with unsupported operation, readonly")
        } catch (e: UnsupportedOperationException) {
        }

        assertEquals("default", testBean.stuff)

        introspection.getProperty("stuff").get().write(testBean, "newvalue")

        assertEquals("newvalue", testBean.stuff)
    }
}