package io.micronaut.jackson.serialize

import tools.jackson.databind.ObjectMapper
import io.micronaut.jackson.ObjectMapperFactory
import spock.lang.Issue
import spock.lang.Specification

class JacksonObjectSerializerSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2282")
    void "test empty optional is returned"() {
        ObjectMapper objectMapper = new ObjectMapperFactory().jsonMapperBuilder(null, null).build()

        when:
        Optional<Object> optional = new JacksonObjectSerializer(objectMapper).deserialize("null".bytes)

        then:
        noExceptionThrown()
        !optional.isPresent()
    }
}
