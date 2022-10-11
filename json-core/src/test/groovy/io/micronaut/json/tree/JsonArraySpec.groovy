package io.micronaut.json.tree

import spock.lang.Specification

import static io.micronaut.json.tree.JsonScalarSpec.thrownException

class JsonArraySpec extends Specification {
    def "array"() {
        given:
        def node = new JsonArray([new JsonNumber(42), new JsonString('foo')])
        def valueIterator = node.values().iterator()

        expect:
        valueIterator.next() == new JsonNumber(42)
        valueIterator.next() == new JsonString('foo')
        !valueIterator.hasNext()

        thrownException { node.entries() } instanceof IllegalStateException
        node.isContainerNode()
        node.isArray()
        !node.isObject()
        node.get("foo") == null
        node.get(0) == new JsonNumber(42)
        node.get(-1) == null
        node.get(2) == null
        node.size() == 2
        !node.isValueNode()
        !node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.coerceStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node == new JsonArray([new JsonNumber(42), new JsonString('foo')])
        node != new JsonArray([new JsonNumber(43), new JsonString('foo')])
        node.hashCode() == new JsonArray([new JsonNumber(42), new JsonString('foo')]).hashCode()
    }
}
