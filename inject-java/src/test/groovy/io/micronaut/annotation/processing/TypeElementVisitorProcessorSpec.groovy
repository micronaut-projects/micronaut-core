package io.micronaut.annotation.processing

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.aop.introduction.Stub
import io.micronaut.http.annotation.Controller
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.Specification

import java.net.URL
import java.util.Collections
import java.util.Enumeration

class TypeElementVisitorProcessorSpec extends Specification {

    void "test get annotation names"() {
        given:
        def visitedAnnotationNames = TypeElementVisitorProcessor.getVisitedAnnotationNames()

        visitedAnnotationNames.forEach(this::println)

        expect:
        visitedAnnotationNames
        visitedAnnotationNames.contains(Stub.name)
        visitedAnnotationNames.contains(Controller.name)
        !visitedAnnotationNames.contains("*")
    }

    void "test core visitors are de-duplicated"() {
        given:
        def warnings = new LinkedHashSet<String>()
        def method = TypeElementVisitorProcessor.getDeclaredMethod("findCoreTypeElementVisitors", Set)
        method.accessible = true

        when:
        Collection<TypeElementVisitor<?, ?>> visitors = (Collection<TypeElementVisitor<?, ?>>) method.invoke(null, warnings)
        List<Class<?>> classes = visitors.collect { it.class }

        then:
        !visitors.isEmpty()
        classes.size() == classes.toSet().size()
    }

    void "test context classloader can control visitor discovery"() {
        given:
        def method = TypeElementVisitorProcessor.getDeclaredMethod("findCoreTypeElementVisitors", Set)
        method.accessible = true
        def previous = Thread.currentThread().contextClassLoader
        def propertyName = "micronaut.processing.use.context.classloader"
        def previousProperty = System.getProperty(propertyName)

        when:
        Thread.currentThread().contextClassLoader = new NoResourcesClassLoader(previous)
        System.setProperty(propertyName, "true")
        Collection<TypeElementVisitor<?, ?>> visitors = (Collection<TypeElementVisitor<?, ?>>) method.invoke(null, [null] as Object[])

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
