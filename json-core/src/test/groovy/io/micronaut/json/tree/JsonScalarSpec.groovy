package io.micronaut.json.tree

import spock.lang.Specification

class JsonScalarSpec extends Specification {
    static Exception thrownException(Closure<?> closure) {
        try {
            closure.run()
        } catch (Exception e) {
            return e;
        }
        throw new AssertionError();
    }

    def "common scalar methods"() {
        expect:
        thrownException { node.values() } instanceof IllegalStateException
        thrownException { node.entries() } instanceof IllegalStateException
        !node.isContainerNode()
        !node.isArray()
        !node.isObject()
        node.get("foo") == null
        node.get(0) == null
        node.size() == 0

        where:
        node << [
                new JsonNumber(42),
                new JsonNumber(12345678901L),
                new JsonNumber(42.5F),
                new JsonNumber(12345678901.5D),
                new JsonNumber(new BigInteger("123456789012345678901234567890")),
                new JsonNumber(new BigDecimal("123456789012345678901234567890.5")),
                JsonNull.INSTANCE,
                JsonBoolean.valueOf(true),
                JsonBoolean.valueOf(false),
                new JsonString("foo")
        ]
    }

    def "int"() {
        given:
        def node = new JsonNumber(42)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "42"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 42
        node.getIntValue() == 42
        node.getLongValue() == 42L
        node.getFloatValue() == 42.0F
        node.getDoubleValue() == 42.0D
        node.getBigIntegerValue() == BigInteger.valueOf(42)
        node.getBigDecimalValue() == BigDecimal.valueOf(42)
        node == new JsonNumber(42)
        node != new JsonNumber(43)
        node.hashCode() == new JsonNumber(42).hashCode()
    }

    def "long"() {
        given:
        def node = new JsonNumber(12345678901L)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "12345678901"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 12345678901L
        node.getIntValue() == -539222987
        node.getLongValue() == 12345678901L
        node.getFloatValue() == 12345678901.0F
        node.getDoubleValue() == 12345678901.0D
        node.getBigIntegerValue() == BigInteger.valueOf(12345678901L)
        node.getBigDecimalValue() == BigDecimal.valueOf(12345678901L)
        node == new JsonNumber(12345678901L)
        node != new JsonNumber(12345678902L)
        node.hashCode() == new JsonNumber(12345678901L).hashCode()
    }

    def "float"() {
        given:
        def node = new JsonNumber(42.5F)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "42.5"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 42.5F
        node.getIntValue() == 42
        node.getLongValue() == 42L
        node.getFloatValue() == 42.5F
        node.getDoubleValue() == 42.5D
        node.getBigIntegerValue() == BigInteger.valueOf(42)
        node.getBigDecimalValue() == BigDecimal.valueOf(42.5)
        node == new JsonNumber(42.5F)
        node != new JsonNumber(42.6F)
        node.hashCode() == new JsonNumber(42.5F).hashCode()
    }

    def "double"() {
        given:
        def node = new JsonNumber(12345678901.5D)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "1.23456789015E10"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 12345678901.5D
        node.getIntValue() == Integer.MAX_VALUE
        node.getLongValue() == 12345678901L
        node.getFloatValue() == 12345678800F
        node.getDoubleValue() == 12345678901.5D
        node.getBigIntegerValue() == BigInteger.valueOf(12345678901L)
        node.getBigDecimalValue() == BigDecimal.valueOf(12345678901.5D)
        node == new JsonNumber(12345678901.5D)
        node != new JsonNumber(12345678901.6D)
        node.hashCode() == new JsonNumber(12345678901.5D).hashCode()
    }

    def "bigint"() {
        given:
        def node = new JsonNumber(new BigInteger("123456789012345678901234567890"))

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "123456789012345678901234567890"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == new BigInteger("123456789012345678901234567890")
        node.getIntValue() == 1312754386
        node.getLongValue() == -4362896299872285998
        node.getFloatValue() == 123456789012345678901234567890.0F
        node.getDoubleValue() == 123456789012345678901234567890.0D
        node.getBigIntegerValue() == new BigInteger("123456789012345678901234567890")
        node.getBigDecimalValue() == new BigDecimal("123456789012345678901234567890")
        node == new JsonNumber(new BigInteger("123456789012345678901234567890"))
        node != new JsonNumber(new BigInteger("123456789012345678901234567891"))
        node.hashCode() == new JsonNumber(new BigInteger("123456789012345678901234567890")).hashCode()
    }

    def "bigdec"() {
        given:
        def node = new JsonNumber(new BigDecimal("123456789012345678901234567890.5"))

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "123456789012345678901234567890.5"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == new BigDecimal("123456789012345678901234567890.5")
        node.getIntValue() == 1312754386
        node.getLongValue() == -4362896299872285998
        node.getFloatValue() == 123456789012345678901234567890.0F // .5 doesn't fit, should be trimmed
        node.getDoubleValue() == 123456789012345678901234567890.0D // .5 doesn't fit, should be trimmed
        node.getBigIntegerValue() == new BigInteger("123456789012345678901234567890")
        node.getBigDecimalValue() == new BigDecimal("123456789012345678901234567890.5")
        node == new JsonNumber(new BigDecimal("123456789012345678901234567890.5"))
        node != new JsonNumber(new BigDecimal("123456789012345678901234567890.6"))
        node.hashCode() == new JsonNumber(new BigDecimal("123456789012345678901234567890.5")).hashCode()
    }

    def "bool"() {
        given:
        def node = JsonBoolean.valueOf(true)

        expect:
        node.isValueNode()
        !node.isNumber()
        !node.isString()
        node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "true"
        node.getBooleanValue()
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node == JsonBoolean.valueOf(true)
        node != JsonBoolean.valueOf(false)
        node.hashCode() == JsonBoolean.valueOf(true).hashCode()
    }

    def "string"() {
        given:
        def node = new JsonString("foo")

        expect:
        node.isValueNode()
        !node.isNumber()
        node.isString()
        !node.isBoolean()
        !node.isNull()
        node.getStringValue() == 'foo'
        node.coerceStringValue() == "foo"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node == new JsonString("foo")
        node != new JsonString("bar")
        node.hashCode() == new JsonString("foo").hashCode()
    }

    def "null"() {
        given:
        def node = JsonNull.INSTANCE

        expect:
        node.isValueNode()
        !node.isNumber()
        !node.isString()
        !node.isBoolean()
        node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.coerceStringValue() == "null"
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node == JsonNull.INSTANCE
        node.hashCode() == JsonNull.INSTANCE.hashCode()
    }
}
