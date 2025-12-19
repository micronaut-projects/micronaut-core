package io.micronaut.json

import tools.jackson.databind.ObjectMapper
import io.micronaut.jackson.ObjectMapperFactory
import io.micronaut.jackson.databind.JacksonDatabindMapper
import spock.lang.Issue
import spock.lang.Specification

class JsonObjectSerializerSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2282")
    void "test empty optional is returned"() {
        ObjectMapper objectMapper = new ObjectMapperFactory().jsonMapperBuilder(null, null).build()

        when:
        Optional<Object> optional = new JsonObjectSerializer(new JacksonDatabindMapper(objectMapper)).deserialize("null".bytes)

        then:
        noExceptionThrown()
        !optional.isPresent()
    }
}
