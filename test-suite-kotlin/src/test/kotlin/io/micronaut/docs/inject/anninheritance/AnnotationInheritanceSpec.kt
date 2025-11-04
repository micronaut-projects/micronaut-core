package io.micronaut.docs.inject.anninheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnotationInheritanceSpec {
    @Test
    fun testAnnotationInheritance() {
        val config = mapOf("datasource.url" to "jdbc://someurl")
        ApplicationContext.run(config).use { context ->
            val beanDefinition = context.getBeanDefinition(BookRepository::class.java)
            val name = beanDefinition.stringValue(AnnotationUtil.NAMED).orElse(null)
            assertEquals("bookRepository", name)
            assertTrue(beanDefinition.isSingleton)
        }
    }
}