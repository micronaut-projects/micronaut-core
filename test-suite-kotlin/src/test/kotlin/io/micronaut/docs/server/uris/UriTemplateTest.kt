package io.micronaut.docs.server.uris

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.micronaut.http.uri.UriTemplateMatcher

class UriTemplateTest: AnnotationSpec() {

    @Test
    fun testUriTemplate() {
        // tag::match[]
        val template = UriTemplateMatcher.of("/hello/{name}")

        template.match("/hello/John").isPresent.shouldBeTrue() // <1>
        template.expand(mapOf("name" to "John")) shouldBe "/hello/John"  // <2>
        // end::match[]
    }
}
