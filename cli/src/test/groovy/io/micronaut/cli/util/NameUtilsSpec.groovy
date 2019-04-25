package io.micronaut.cli.util

import spock.lang.Specification

class NameUtilsSpec extends Specification {

    void "test natural name"() {
        expect:
        NameUtils.getNaturalName(name) == result

        where:
        name         | result
        "aName"      | "A Name"
        "name"       | "Name"
        "firstName"  | "First Name"
        "URL"        | "URL"
        "localURL"   | "Local URL"
        "URLLocal"   | "URL Local"
        "aURLLocal"  | "A URL Local"
        "MyDomainClass"                | "My Domain Class"
        "com.myco.myapp.MyDomainClass" | "My Domain Class"
    }
}
