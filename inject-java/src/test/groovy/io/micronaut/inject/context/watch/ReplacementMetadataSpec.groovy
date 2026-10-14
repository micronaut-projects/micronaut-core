package io.micronaut.inject.context.watch

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.watch.BeanDefinitionChange
import io.micronaut.context.watch.BeanExecutableMethod
import io.micronaut.context.watch.ExecutableMethodChange
import io.micronaut.inject.BeanDefinition

class ReplacementMetadataSpec extends AbstractTypeElementSpec {

    void "a method replacement whose parameter annotations change reports the annotations changed"() {
        expect:
        !replacement(target('@jakarta.inject.Named("before") String name'), target('@jakarta.inject.Named("after") String name')).metadataUnchanged()

        and: "the same parameter annotations compare as unchanged"
        replacement(target('@jakarta.inject.Named("before") String name'), target('@jakarta.inject.Named("before") String name')).metadataUnchanged()
    }

    void "a method replacement whose parameter type argument annotations change reports the annotations changed"() {
        expect:
        !replacement(target('java.util.List<@org.jspecify.annotations.Nullable String> name'), target('java.util.List<String> name')).metadataUnchanged()
    }

    void "a method replacement whose return type annotations change reports the annotations changed"() {
        expect:
        !replacement(target('String name', '@org.jspecify.annotations.Nullable String'), target('String name', 'String')).metadataUnchanged()
    }

    void "a change of generic arity without annotations leaves the annotations unchanged"() {
        expect: "String to List<String>, a change of structure only"
        replacement(target('String name', 'String'), target('String name', 'java.util.List<String>')).metadataUnchanged()

        and: "an annotated type argument that goes with the arity is a change of annotations"
        !replacement(target('String name', 'java.util.List<@org.jspecify.annotations.Nullable String>'), target('String name', 'String')).metadataUnchanged()
    }

    void "a bean replacement compares the annotations of the bean"() {
        given:
        BeanDefinition<?> before = target('String name')
        BeanDefinition<?> after = target('String name')

        expect:
        new BeanDefinitionChange.Replacement(before, after).metadataUnchanged()
    }

    private BeanDefinition<?> target(String parameter, String returnType = 'String') {
        buildBeanDefinition('test.MetadataTarget', """
package test;

@jakarta.inject.Singleton
class MetadataTarget {
    @io.micronaut.context.annotation.Executable
    public ${returnType} read(${parameter}) {
        return null;
    }
}
""")
    }

    private static ExecutableMethodChange.Replacement<?> replacement(BeanDefinition<?> before, BeanDefinition<?> after) {
        def first = new BeanExecutableMethod(before, before.executableMethods.find { it.methodName == 'read' })
        def second = new BeanExecutableMethod(after, after.executableMethods.find { it.methodName == 'read' })
        assert first.sameMethod(second)
        new ExecutableMethodChange.Replacement(first, second)
    }
}
