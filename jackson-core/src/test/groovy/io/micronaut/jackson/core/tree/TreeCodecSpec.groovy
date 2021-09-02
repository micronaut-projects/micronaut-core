package io.micronaut.jackson.core.tree

import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class TreeCodecSpec extends Specification {
    def readTree() {
        given:
        def c = MicronautTreeCodec.getInstance()

        expect:
        c.readTree(new JsonFactory().createParser('"bar"')) == JsonNode.createStringNode("bar")
        c.readTree(new JsonFactory().createParser('42')) == JsonNode.createNumberNode(42)
        c.readTree(new JsonFactory().createParser('true')) == JsonNode.createBooleanNode(true)
        c.readTree(new JsonFactory().createParser('null')) == JsonNode.nullNode()
        c.readTree(new JsonFactory().createParser('{"foo":"bar","x":42}')) ==
                JsonNode.createObjectNode(["foo": JsonNode.createStringNode("bar"), "x": JsonNode.createNumberNode(42)])
        c.readTree(new JsonFactory().createParser('["bar",42]')) ==
                JsonNode.createArrayNode([JsonNode.createStringNode("bar"), JsonNode.createNumberNode(42)])
    }
}
