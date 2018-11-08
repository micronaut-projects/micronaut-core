package io.micronaut.jackson.convert

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import spock.lang.Specification

class JsonNodeToObjectConverterSpec extends Specification {

    void "test the converter handles NullNode correctly"() {
        given:
        def converter = new JsonNodeToObjectConverter(new ObjectMapper())

        when:
        Optional optional = converter.convert(NullNode.instance, Pojo.class)

        then:
        noExceptionThrown()
        !optional.isPresent()
    }

    class Pojo {

    }
}
