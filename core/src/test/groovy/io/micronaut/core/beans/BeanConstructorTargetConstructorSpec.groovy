package io.micronaut.core.beans

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.reflect.Constructor

class BeanConstructorTargetConstructorSpec extends Specification {

    static class Widget {
        Widget(String name, int size) {
        }
    }

    void "the default resolves the declared constructor from the declaring type and the raw argument types"() {
        given:
        BeanConstructor<Widget> constructor = new BeanConstructor<Widget>() {
            @Override
            Class<Widget> getDeclaringBeanType() { Widget }

            @Override
            Argument<?>[] getArguments() { [Argument.of(String, "name"), Argument.of(int, "size")] as Argument[] }

            @Override
            Widget instantiate(Object... parameterValues) { new Widget(parameterValues[0] as String, parameterValues[1] as int) }

            @Override
            AnnotationMetadata getAnnotationMetadata() { AnnotationMetadata.EMPTY_METADATA }
        }

        expect:
        constructor.targetConstructor == Widget.getDeclaredConstructor(String, int)
    }

    void "the default is null when the declaring type declares no such constructor"() {
        given:
        BeanConstructor<Widget> constructor = new BeanConstructor<Widget>() {
            @Override
            Class<Widget> getDeclaringBeanType() { Widget }

            @Override
            Argument<?>[] getArguments() { [Argument.of(String, "name")] as Argument[] }

            @Override
            Widget instantiate(Object... parameterValues) { null }

            @Override
            AnnotationMetadata getAnnotationMetadata() { AnnotationMetadata.EMPTY_METADATA }
        }

        expect:
        constructor.targetConstructor == null
    }

    void "the abstract constructor resolves once, whether or not there is a constructor"() {
        given:
        def matching = new AbstractBeanConstructor<Widget>(Widget, null, Argument.of(String, "name"), Argument.of(int, "size")) {
            @Override
            Widget instantiate(Object... parameterValues) { null }
        }
        def unmatched = new AbstractBeanConstructor<Widget>(Widget, null, Argument.of(String, "name")) {
            @Override
            Widget instantiate(Object... parameterValues) { null }
        }

        expect:
        matching.targetConstructor == Widget.getDeclaredConstructor(String, int)
        matching.targetConstructor.is(matching.targetConstructor)
        unmatched.targetConstructor == null
    }

    void "the cache runs the resolution once and holds a null result"() {
        given:
        int resolutions = 0
        def cache = new TargetConstructorCache<Widget>()
        Constructor<Widget> ctor = Widget.getDeclaredConstructor(String, int)

        when:
        def first = cache.get { resolutions++; ctor }
        def second = cache.get { resolutions++; null }

        then:
        first.is(ctor)
        second.is(ctor)
        resolutions == 1

        when:
        def empty = new TargetConstructorCache<Widget>()
        int emptyResolutions = 0
        def a = empty.get { emptyResolutions++; null }
        def b = empty.get { emptyResolutions++; ctor }

        then:
        a == null
        b == null
        emptyResolutions == 1
    }
}
