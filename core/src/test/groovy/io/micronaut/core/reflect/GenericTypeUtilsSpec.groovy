package io.micronaut.core.reflect

import spock.lang.Specification

class GenericTypeUtilsSpec extends Specification {

    void "test resolve generic super type"() {
        expect:
        GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Bar) == [String] as Class[]
    }

    static class Foo<T> {}

    static class Bar<T> extends Foo<T> {}

    static class Baz extends Bar<String> {}
}

