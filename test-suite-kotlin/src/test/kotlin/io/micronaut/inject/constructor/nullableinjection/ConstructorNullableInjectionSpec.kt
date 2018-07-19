package io.micronaut.inject.constructor.nullableinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import junit.framework.TestCase

class ConstructorNullableInjectionSpec : TestCase() {

    fun testNullableInjectionInConstructor() {
        val context = BeanContext.run()
        val b = context.getBean(B::class.java)
        TestCase.assertNull(b.a)
    }

    fun testNormalInjectionStillFails() {
        val context = BeanContext.run()
        try {
            context.getBean(C::class.java)
            TestCase.fail("Expected a DependencyInjectionException to be thrown")
        } catch (e: DependencyInjectionException) {}
    }
}
