package io.micronaut.docs.expressions

import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = false)
class AnnotationContextExampleTest {

    @Inject
    lateinit var beanContext: BeanContext

    @Test
    fun testAnnotationContextEvaluation() {
        val beanDefinition = beanContext.getBeanDefinition(Example::class.java)
        val value = beanDefinition.stringValue(CustomAnnotation::class.java).orElse(null)
        Assertions.assertEquals(value, "first valuesecond value")
    }
}
