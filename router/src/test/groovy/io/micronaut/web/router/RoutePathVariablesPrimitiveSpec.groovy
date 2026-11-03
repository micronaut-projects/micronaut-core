package io.micronaut.web.router

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultMutableConversionService
import spock.lang.Specification

class RoutePathVariablesPrimitiveSpec extends Specification {

    Map<String, Object> values = [id: "12345", big: "9000000000", ratio: "1.5", flag: "on", bad: "x", empty: "", obj: 7,
                                  padded: "0000000000000000000000000000000000000042", longBad: "x" * 40]

    // a subclass of the conversion service converts by boxing: it does not share primitive converters
    ConversionService boxingService = new DefaultMutableConversionService() {}
    RoutePathVariables plain = new RoutePathVariables(values, values, boxingService)
    RoutePathVariables primitive = new RoutePathVariables(values, values, new DefaultMutableConversionService())

    void "the primitive accessors return what they return without the converters"() {
        expect:
        !(boxingService.resolveConverter(String, io.micronaut.core.type.Argument.INT) instanceof io.micronaut.core.convert.PrimitiveTargetTypeConverter)
        primitive.getInt("id") == plain.getInt("id")
        primitive.getInt("id") == 12345
        primitive.getInt("obj") == plain.getInt("obj")
        primitive.getInt("missing", 3) == 3
        primitive.getInt("id", 3) == 12345
        primitive.findInt("id") == OptionalInt.of(12345)
        primitive.findInt("missing").isEmpty()
        primitive.getLong("big") == 9000000000L
        primitive.getLong("missing", 4L) == 4L
        primitive.findLong("big") == OptionalLong.of(9000000000L)
        primitive.getDouble("ratio") == 1.5d
        primitive.getDouble("missing", 2d) == 2d
        primitive.findDouble("ratio") == OptionalDouble.of(1.5d)
        primitive.getBoolean("flag")
        !primitive.getBoolean("missing", false)
        primitive.raw().getInt("id") == 12345
        primitive.getInt("padded") == plain.getInt("padded")
        primitive.getInt("padded") == 42
    }

    void "a converter that replaces the built-in parser converts with the context of the variable"() {
        given: "a converter that reads the argument of its context, and rejects a value it still returns"
        def service = new DefaultMutableConversionService()
        service.addInternalConverter(CharSequence, Integer, { CharSequence s, Class<Integer> t, io.micronaut.core.convert.ConversionContext c ->
            if (s == "bad") {
                c.reject(s, new IllegalArgumentException("rejected"))
                return Optional.of(-1)
            }
            return Optional.of(((io.micronaut.core.convert.ArgumentConversionContext) c).argument.name.length())
        } as io.micronaut.core.convert.TypeConverter)
        def variables = new RoutePathVariables([id: "12345", bad: "bad"], [id: "12345", bad: "bad"], service)

        expect:
        variables.getInt("id") == 2
        variables.findInt("id") == OptionalInt.of(2)

        when:
        variables.getInt("bad")

        then:
        def e = thrown(io.micronaut.core.convert.exceptions.ConversionErrorException)
        e.message.contains("rejected")
    }

    void "a value that does not convert fails as without the converters"() {
        when:
        plain."$method"(*args)

        then:
        def expected = thrown(Exception)

        when:
        primitive."$method"(*args)

        then:
        def actual = thrown(Exception)
        actual.class == expected.class
        actual.message == expected.message

        where:
        method      | args
        "getInt"    | ["bad"]
        "getInt"    | ["empty"]
        "getInt"    | ["missing"]
        "getInt"    | ["bad", 3]
        "findInt"   | ["bad"]
        "getLong"   | ["bad"]
        "getLong"   | ["missing"]
        "findLong"  | ["empty"]
        "getDouble" | ["bad"]
        "getDouble" | ["missing"]
        "getInt"    | ["longBad"]
    }
}
