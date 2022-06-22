package io.micronaut.context

import spock.lang.Specification

class MessageSourceUtilsSpec extends Specification {
    void "given a list MessageSourceUtils::variables returns a map whose key is a string with the numeric value of the item's position"() {
        expect:
        MessageSourceUtils.variables("Sergio", "John") == ["0": 'Sergio', "1": "John"]
        MessageSourceUtils.variables("Sergio", "John") == ["0": 'Sergio', "1": "John"]
    }
}