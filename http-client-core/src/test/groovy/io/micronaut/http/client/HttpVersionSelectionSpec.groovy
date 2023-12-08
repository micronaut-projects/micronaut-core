package io.micronaut.http.client


import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.http.client.annotation.Client
import spock.lang.Specification

class HttpVersionSelectionSpec extends Specification {
    def 'annotation equals'() {
        given:
        def introspection = BeanIntrospector.SHARED.findIntrospection(TestClass).get()
        def s1 = HttpVersionSelection.forClientAnnotation(introspection)
        def s2 = HttpVersionSelection.forClientAnnotation(introspection)

        expect:
        s1 == s2
    }

    @Client(alpnModes = ["h2"], plaintextMode = HttpVersionSelection.PlaintextMode.HTTP_1)
    @Introspected
    static interface TestClass {
    }
}
