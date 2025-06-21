package io.micronaut.core.beans

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class RecusiveGenericsSpec {

    // issue https://github.com/micronaut-projects/micronaut-core/issues/1607
    @Test
    fun testRecursiveGenericsOnBeanIntrospection() {
        val introspection = BeanIntrospection.getIntrospection(Item::class.java)
        // just check compilation works
        assertNotNull(introspection)
    }
}
