package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.io.scan.BeanIntrospectionScanner
import spock.lang.Specification

class BeanIntrospectionScannerSpec extends Specification {

    void "test bean introspection scanner finds types"() {
        given:
        BeanIntrospectionScanner scanner = new BeanIntrospectionScanner()

        expect:
        scanner.scan(Introspected.class, getClass().getPackage())
            .count() > 0
    }
}
