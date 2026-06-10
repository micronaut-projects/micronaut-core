package io.micronaut.docs.ioc.introspection

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntrospectedPropertyTest {

    @Test
    fun testIntrospectedPropertyMetadata() {
        // tag::metadata[]
        val introspection = BeanIntrospection.getIntrospection(Book::class.java)
        val property = introspection.getRequiredProperty("title", String::class.java)
        val externalName = property.stringValue(Introspected.Property::class.java, "name")
        // end::metadata[]

        assertEquals("book_title", externalName.orElseThrow())
    }

    @Test
    fun testJacksonPropertyMetadata() {
        val introspection = BeanIntrospection.getIntrospection(User::class.java)
        val property = introspection.getRequiredProperty("displayName", String::class.java)

        assertEquals("display_name", property.stringValue(Introspected.Property::class.java, "name").orElseThrow())
    }
}
