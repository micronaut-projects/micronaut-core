package io.micronaut.core.util

import spock.lang.Specification
import spock.lang.Unroll

class StringUtilsSpec extends Specification {

    @Unroll
    void "test convertDotToUnderscore"() {

        expect:
        result == StringUtils.convertDotToUnderscore(value)

        where:
        value                    | result
        ""                       | ""
        "micronaut.config.files" | "MICRONAUT_CONFIG_FILES"
        "micronaut"              | "MICRONAUT"
        null                     | null
    }
}
