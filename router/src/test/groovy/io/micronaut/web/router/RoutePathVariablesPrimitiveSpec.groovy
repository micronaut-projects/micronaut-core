package io.micronaut.web.router

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.http.bind.PrimitiveValueConverters
import spock.lang.Specification

class RoutePathVariablesPrimitiveSpec extends Specification {

    ConversionService service = new DefaultMutableConversionService()
    Map<String, Object> values = [id: "12345", big: "9000000000", ratio: "1.5", flag: "on", bad: "x", empty: "", obj: 7]

    RoutePathVariables plain = new RoutePathVariables(values, values, service)
    RoutePathVariables primitive = new RoutePathVariables(values, values, service, PrimitiveValueConverters.of(service))

    void "the primitive accessors return what they return without the converters"() {
        expect:
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
    }
}
