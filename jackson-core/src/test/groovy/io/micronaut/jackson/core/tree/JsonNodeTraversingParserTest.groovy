package io.micronaut.jackson.core.tree

import tools.jackson.core.Base64Variants
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class JsonNodeTraversingParserTest extends Specification {

    def getBinaryValue() {
        given:
        def parser = new JsonNodeTraversingParser(jsonNode)

        parser.nextToken()

        def binaryValue = parser.getBinaryValue(Base64Variants.MIME)

        expect:
        binaryValue == expected?.bytes

        where:
        jsonNode                           || expected
        JsonNode.createStringNode("YWJj")  || "abc"
        JsonNode.nullNode()                || null
    }

}
