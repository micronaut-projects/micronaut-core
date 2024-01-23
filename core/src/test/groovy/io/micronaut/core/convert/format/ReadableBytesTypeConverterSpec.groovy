package io.micronaut.core.convert.format

import spock.lang.Specification
import spock.lang.Unroll

class ReadableBytesTypeConverterSpec extends Specification {

    @Unroll
    void "test conversion readable bytes"() {
        given:
        def converter = new ReadableBytesTypeConverter()

        when:
        def converted = converter.convert(value, Long.class, null).orElse(null)

        then:

        converted
        converted == mustBe

        where:
        value    | mustBe
        "100kb"  | 100 * 1024L
        "1000Gb" | 1000 * 1024 * 1024 * 1024L
        "10"     | 10L
        "10MB"   | 10 * 1024 * 1024L
    }
}
