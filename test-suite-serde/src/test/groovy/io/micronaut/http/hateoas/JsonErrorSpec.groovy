package io.micronaut.http.hateoas

import io.micronaut.jackson.databind.JacksonDatabindMapper
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import spock.lang.PendingFeature
import spock.lang.Specification

class JsonErrorSpec extends Specification {

    def jsonError = '{"_links":{"self":[{"href":"/resolve","templated":false}]},"_embedded":{"errors":[{"message":"Internal Server Error: Something bad happened"}]},"message":"Internal Server Error"}'

    @PendingFeature
    void "JsonError should be deserializable from a string - serde"() {
        setup:
            ObjectMapper objectMapper = ObjectMapper.getDefault()
        when:
            JsonError jsonError = objectMapper.readValue(this.jsonError, JsonError)

        then:
            jsonError.message == 'Internal Server Error'
            jsonError.embedded.getFirst('errors').isPresent()
            jsonError.links.getFirst("self").get().href == "/resolve"
            !jsonError.links.getFirst("self").get().templated
    }

    @PendingFeature
    void "can deserialize a Json error as a generic resource - serde"() {
        setup:
            ObjectMapper objectMapper = ObjectMapper.getDefault()
        when:
            GenericResource resource = objectMapper.readValue(jsonError, Resource)
        then:
            resource.embedded.getFirst('errors').isPresent()
            resource.links.getFirst("self").get().href == "/resolve"
            !resource.links.getFirst("self").get().templated
    }

    void "JsonError should be deserializable from a string - jackson databind"() {
        setup:
            JsonMapper objectMapper = new JacksonDatabindMapper(new com.fasterxml.jackson.databind.ObjectMapper())

        when:
            JsonError jsonError = objectMapper.readValue(this.jsonError, JsonError)

        then:
            jsonError.message == 'Internal Server Error'
            jsonError.embedded.getFirst('errors').isPresent()
            jsonError.links.getFirst("self").get().href == "/resolve"
            !jsonError.links.getFirst("self").get().templated
    }

    void "can deserialize a Json error as a generic resource - jackson databind"() {
        setup:
            JsonMapper objectMapper = new JacksonDatabindMapper(new com.fasterxml.jackson.databind.ObjectMapper())
        when:
            GenericResource resource = objectMapper.readValue(jsonError, Resource)
        then:
            resource.embedded.getFirst('errors').isPresent()
            resource.links.getFirst("self").get().href == "/resolve"
            !resource.links.getFirst("self").get().templated
    }
}
