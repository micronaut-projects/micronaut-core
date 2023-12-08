package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions;

class JavaxTransientTest {
    @Test
    fun testIntrospectionWithJavaxTransient() {
        val introspection: BeanIntrospection<ObjectWithJavaxTransient> = BeanIntrospection.getIntrospection(ObjectWithJavaxTransient::class.java);
        Assertions.assertTrue(introspection.getProperty("tmp").isPresent())
    }
}
