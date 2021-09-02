package io.micronaut.jackson.core.tree

import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class TreeGeneratorSpec extends Specification {
    def scalar() {
        given:
        def gen = MicronautTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeString("abc")

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createStringNode("abc")
    }

    def array() {
        given:
        def gen = MicronautTreeCodec.getInstance().createTreeGenerator()

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
        def gen = MicronautTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeFieldName("foo")
        gen.writeString("abc")
        gen.writeFieldName("bar")
        gen.writeNumber(123)
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue() == JsonNode.createObjectNode([
                "foo": JsonNode.createStringNode("abc"),
                "bar": JsonNode.createNumberNode(123)])
    }

    def nested() {
        given:
        def gen = MicronautTreeCodec.getInstance().createTreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeFieldName("foo")
        gen.writeStartObject()
        gen.writeFieldName("bar")
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
