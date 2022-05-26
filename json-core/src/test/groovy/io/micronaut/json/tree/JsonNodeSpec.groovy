package io.micronaut.json.tree

import spock.lang.Specification

class JsonNodeSpec extends Specification {
    def factories() {
        expect:
        JsonNode.nullNode().isNull()
        JsonNode.createBooleanNode(true).booleanValue
        JsonNode.createNumberNode(123).numberValue == 123
        JsonNode.createNumberNode(123L).numberValue == 123L
        JsonNode.createNumberNode(123.5D).numberValue == 123.5D
        JsonNode.createNumberNode(123.5F).numberValue == 123.5F
        JsonNode.createNumberNode(BigInteger.ONE).numberValue == BigInteger.ONE
        JsonNode.createNumberNode(BigDecimal.valueOf(0.5)).numberValue == BigDecimal.valueOf(0.5)
        JsonNode.createStringNode('foo').stringValue == 'foo'

        JsonNode.createArrayNode([JsonNode.nullNode()]).size() == 1
        JsonNode.createArrayNode([JsonNode.nullNode()]).get(0).isNull()

        JsonNode.createObjectNode(['foo': JsonNode.nullNode()]).size() == 1
        JsonNode.createObjectNode(['foo': JsonNode.nullNode()]).get('foo').isNull()
    }
}
