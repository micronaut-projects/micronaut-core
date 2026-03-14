package io.micronaut.jackson.core.tree

import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

import java.util.stream.Collectors

class TreeGeneratorSpec extends Specification {
    def scalar() {
        given:
        def gen = JsonNodeTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeString("abc")

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createStringNode("abc")
    }

    def array() {
        given:
        def gen = JsonNodeTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartArray()
        gen.writeString("abc")
        gen.writeNumber(123)
        gen.writeEndArray()

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createArrayNode([
                JsonNode.createStringNode("abc"),
                JsonNode.createNumberNode(123)])
    }

    def object() {
        given:
        def gen = JsonNodeTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeName("foo")
        gen.writeString("abc")
        gen.writeName("bar")
        gen.writeNumber(123)
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createObjectNode([
                "foo": JsonNode.createStringNode("abc"),
                "bar": JsonNode.createNumberNode(123)])
    }

    def 'object order'() {
        given:
        def gen = JsonNodeTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeName("2")
        gen.writeString('')
        gen.writeName("1")
        gen.writeString('')
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue().entries().toList().stream().map { it.key }.collect(Collectors.toList())
                == ['2', '1']
    }

    def nested() {
        given:
        def gen = JsonNodeTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeName("foo")
        gen.writeStartObject()
        gen.writeName("bar")
        gen.writeNumber(123)
        gen.writeEndObject()
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createObjectNode([
                "foo": JsonNode.createObjectNode([
                        "bar": JsonNode.createNumberNode(123)])])
    }
}
