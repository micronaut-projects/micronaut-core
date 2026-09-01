package io.micronaut.docs.inject.aliasfor

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComposedZipSpec {

    @Test
    fun testTheAliasedMemberDefaultOverridesTheDeclaredStereotypeValue() {
        ApplicationContext.run().use { context ->
            val definition = context.getBeanDefinition(ZipCodeValidator::class.java)
            assertEquals(5, definition.intValue(Size::class.java, "min").orElse(-1))
            assertEquals(10, definition.intValue(Size::class.java, "max").orElse(-1))
        }
    }

    @Test
    fun testAnExplicitlySetMemberOverridesTheDeclaredStereotypeValue() {
        ApplicationContext.run().use { context ->
            val definition = context.getBeanDefinition(CustomZipCodeValidator::class.java)
            assertEquals(5, definition.intValue(Size::class.java, "min").orElse(-1))
            assertEquals(20, definition.intValue(Size::class.java, "max").orElse(-1))
        }
    }
}
