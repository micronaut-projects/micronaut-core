package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnnotationContextExampleSpec {

    @Test
    fun testAnnotationContextEvaluation() {
        ApplicationContext.run().use { beanContext ->
            val beanDefinition = beanContext.getBeanDefinition(Example::class.java)
            val value = beanDefinition.stringValue(CustomAnnotation::class.java).orElse(null)
            assertEquals("first valuesecond value", value)
        }
    }
}
