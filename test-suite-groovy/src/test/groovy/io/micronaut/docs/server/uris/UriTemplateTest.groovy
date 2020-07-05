package io.micronaut.docs.server.uris

import io.micronaut.http.uri.UriMatchTemplate
import spock.lang.Specification

class UriTemplateTest extends Specification {

    void "test uri template"() {
        // tag::match[]
        given:
        UriMatchTemplate template = UriMatchTemplate.of("/hello/{name}")

        expect:
        template.match("/hello/John").isPresent() // <1>
        template.expand(["name": "John"]) == "/hello/John" // <2>
        // end::match[]
    }

    void "test uri with slash before params"() {
        // tag::match[]
        given:
        UriMatchTemplate template = UriMatchTemplate.of("/hello/{name}")

        expect:
        template.match("/hello/John/?param=value").isPresent() // <1>
        // end::match[]
    }
}
