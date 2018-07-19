package io.micronaut.inject.field.nullableinjection

import io.micronaut.context.BeanContext
import junit.framework.TestCase

class FieldNullableInjectionSpec : TestCase() {

    fun testNullableFieldInjection() {
        val context = BeanContext.run()
        val b = context.getBean(B::class.java)
        TestCase.assertNull(b.a)
    }

}
