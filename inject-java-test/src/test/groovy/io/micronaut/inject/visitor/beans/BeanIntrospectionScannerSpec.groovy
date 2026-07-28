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

    void "test bean introspection scanner uses explicit classloader"() {
        given:
        String property = "micronaut.introspections.use.context.classloader"
        String previousProperty = System.getProperty(property)
        ClassLoader previousClassLoader = Thread.currentThread().contextClassLoader
        BeanIntrospectionScanner scanner = new BeanIntrospectionScanner(getClass().classLoader)

        when:
        System.setProperty(property, "true")
        Thread.currentThread().contextClassLoader = new URLClassLoader(new URL[0], null)

        then:
        scanner.scan(Introspected.class, getClass().getPackage())
            .count() > 0

        cleanup:
        Thread.currentThread().contextClassLoader = previousClassLoader
        if (previousProperty == null) {
            System.clearProperty(property)
        } else {
            System.setProperty(property, previousProperty)
        }
    }
}
