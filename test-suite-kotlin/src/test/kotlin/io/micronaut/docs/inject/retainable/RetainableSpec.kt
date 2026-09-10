package io.micronaut.docs.inject.retainable

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetainableSpec {

    @Test
    fun testEachComposedAnnotationReportsTheOccurrenceItIntroduced() {
        ApplicationContext.run().use { context ->
            val definition = context.getBeanDefinition(CodeValidator::class.java)

            //tag::read[]
            val min = definition.getAnnotation(MinLength::class.java)!!.stereotypes!!.first()
            val max = definition.getAnnotation(MaxLength::class.java)!!.stereotypes!!.first()

            assertEquals(Limit::class.java.name, min.annotationName)
            assertEquals(mapOf("min" to 3), min.values) // @Limit(min = 3)
            assertEquals(mapOf("max" to 9), max.values) // @Limit(max = 9)
            //end::read[]
        }
    }

    @Test
    fun testTheFlatIndexCannotAttributeTheOccurrences() {
        ApplicationContext.run().use { context ->
            val definition = context.getBeanDefinition(CodeValidator::class.java)

            assertEquals(
                setOf(MinLength::class.java.name, MaxLength::class.java.name),
                definition.getAnnotationNamesByStereotype(Limit::class.java).toSet()
            )
        }
    }
}
