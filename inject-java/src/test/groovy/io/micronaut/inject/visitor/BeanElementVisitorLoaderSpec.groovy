package io.micronaut.inject.visitor

import spock.lang.Specification

import java.net.URL
import java.util.Collections
import java.util.Enumeration

class BeanElementVisitorLoaderSpec extends Specification {

    void "test context classloader can disable bean visitor discovery"() {
        given:
        def previous = Thread.currentThread().contextClassLoader
        def propertyName = VisitorContext.MICRONAUT_PROCESSING_USE_CONTEXT_CLASSLOADER
        def previousProperty = System.getProperty(propertyName)

        when:
        Thread.currentThread().contextClassLoader = new NoResourcesClassLoader(previous)
        System.setProperty(propertyName, "true")
        def visitors = BeanElementVisitorLoader.load()

        then:
        visitors.isEmpty()

        cleanup:
        Thread.currentThread().contextClassLoader = previous
        if (previousProperty == null) {
            System.clearProperty(propertyName)
        } else {
            System.setProperty(propertyName, previousProperty)
        }
    }

    private static final class NoResourcesClassLoader extends ClassLoader {
        NoResourcesClassLoader(ClassLoader parent) {
            super(parent)
        }

        @Override
        Enumeration<URL> getResources(String name) {
            return Collections.emptyEnumeration()
        }

        @Override
        URL getResource(String name) {
            return null
        }
    }
}
