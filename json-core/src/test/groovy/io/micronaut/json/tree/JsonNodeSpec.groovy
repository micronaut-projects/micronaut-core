package io.micronaut.json.tree

import spock.lang.Specification

class JsonNodeSpec extends Specification {
    def factories() {
        expect:
        JsonNode.nullNode().isNull()
        JsonNode.nullNode().getValue() == null

        JsonNode.createBooleanNode(true).booleanValue
        JsonNode.createBooleanNode(true).getValue()
        JsonNode.createNumberNode(123).numberValue == 123
        JsonNode.createNumberNode(123).getValue() == 123
        JsonNode.createNumberNode(123L).numberValue == 123L
        JsonNode.createNumberNode(123.5D).numberValue == 123.5D
        JsonNode.createNumberNode(123.5F).numberValue == 123.5F
        JsonNode.createNumberNode(123.5F).getValue() == 123.5F
        JsonNode.createNumberNode(BigInteger.ONE).numberValue == BigInteger.ONE
        JsonNode.createNumberNode(BigInteger.ONE).getValue() == BigInteger.ONE
        JsonNode.createNumberNode(BigDecimal.valueOf(0.5)).numberValue == BigDecimal.valueOf(0.5)
        JsonNode.createStringNode('foo').stringValue == 'foo'
        JsonNode.createStringNode('foo').getValue() == 'foo'

        JsonNode.createArrayNode([JsonNode.nullNode()]).size() == 1
        JsonNode.createArrayNode([JsonNode.nullNode()]).getValue() == [null]
        JsonNode.createArrayNode([JsonNode.nullNode()]).get(0).isNull()

        JsonNode.createObjectNode(['foo': JsonNode.nullNode()]).size() == 1
        JsonNode.createObjectNode(['foo': JsonNode.nullNode()]).getValue() == ['foo': null]
        JsonNode.createObjectNode(['foo': JsonNode.nullNode()]).get('foo').isNull()
    }

    def construct() {
        expect:
        JsonNode.from(null).isNull()
        JsonNode.from(true).isBoolean()
        JsonNode.from(123).isNumber()
        JsonNode.from(BigInteger.ONE).isNumber()
        JsonNode.from([null]).isArray()
        JsonNode.from([null]).get(0).isNull()
        JsonNode.from(['foo': null]).isObject()
        JsonNode.from(['foo': null]).get("foo").isNull()
        JsonNode.from(['foo': BigInteger.ONE]).get("foo").isNumber()
        JsonNode.from(['foo': ['bar': 123]]).get("foo").get("bar").isNumber()
    }
}
