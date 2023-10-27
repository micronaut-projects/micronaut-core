package io.micronaut.docs.server.uris

import io.micronaut.http.uri.UriMatchTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UriTemplateTest {

    @Test
    fun testUriTemplate() {
        // tag::match[]
        val template = UriMatchTemplate.of("/hello/{name}")

        assertTrue(template.match("/hello/John").isPresent) // <1>
        assertEquals("/hello/John", template.expand(mapOf("name" to "John")))  // <2>
        // end::match[]
    }
}
