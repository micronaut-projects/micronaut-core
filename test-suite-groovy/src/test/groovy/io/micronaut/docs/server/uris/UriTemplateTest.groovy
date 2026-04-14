package io.micronaut.docs.server.uris


import io.micronaut.http.uri.UriTemplateMatcher
import spock.lang.Specification

class UriTemplateTest extends Specification {

    void "test uri template"() {
        // tag::match[]
        given:
        UriTemplateMatcher template = UriTemplateMatcher.of("/hello/{name}")

        expect:
        template.match("/hello/John").isPresent() // <1>
        template.expand(["name": "John"]) == "/hello/John" // <2>
        // end::match[]
    }

    void "test uri with slash before params"() {
        given:
        UriTemplateMatcher template = UriTemplateMatcher.of("/hello/{name}")

        expect:
        template.match("/hello/John/?param=value").isPresent()
    }
}
