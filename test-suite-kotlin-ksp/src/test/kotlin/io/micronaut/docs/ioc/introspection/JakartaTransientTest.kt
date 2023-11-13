package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions

class JakartaTransientTest {
    @Test
    fun testIntrospectionWithJakartaTransient() {
        val introspection: BeanIntrospection<ObjectWithJakartaTransient> = BeanIntrospection.getIntrospection(ObjectWithJakartaTransient::class.java);
        Assertions.assertTrue(introspection.getProperty("tmp").isPresent())
    }
}
