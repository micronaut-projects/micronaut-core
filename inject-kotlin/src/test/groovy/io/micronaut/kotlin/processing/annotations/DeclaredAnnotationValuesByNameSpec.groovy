package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

/**
 * {@code getDeclaredAnnotationValuesByName} answers exactly like {@code getAnnotationValuesByName} for an
 * annotation the element declares itself: a non-repeatable annotation is a single-element list, a repeatable
 * one lists its occurrences whether written once, twice or through its container, and a missing name is empty.
 */
class DeclaredAnnotationValuesByNameSpec extends AbstractKotlinCompilerSpec {

    private static final String PKG = 'byname'
    private static final String FOO = PKG + '.Foo'
    private static final String Q = PKG + '.Q'
    private static final String MISSING = PKG + '.Missing'

    private static String source(String annotations) {
        """
package $PKG

import jakarta.inject.Singleton

@Retention(AnnotationRetention.RUNTIME)
annotation class Foo(val value: String)

@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(Qs::class)
annotation class Q(val value: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class Qs(val value: Array<Q>)

@Singleton
$annotations
class Bar
"""
    }

    void "declared and inherited queries by name agree at compile time for #description"() {
        given:
        AnnotationMetadata metadata = buildClassElement("${PKG}.Bar", source(annotations)).getAnnotationMetadata()

        expect:
        probe(metadata, name) == [declared: expected, all: expected]

        where:
        description                        | annotations                 | name    || expected
        'a non-repeatable annotation'      | '@Foo("x")'                 | FOO     || ['x']
        'a repeatable written once'        | '@Q("a")'                   | Q       || ['a']
        'a repeatable written twice'       | '@Q("a") @Q("b")'           | Q       || ['a', 'b']
        'a container written by hand'      | '@Qs([Q("a"), Q("b")])'     | Q       || ['a', 'b']
        'a missing annotation'             | '@Foo("x")'                 | MISSING || []
        'a repeatable that is not present' | '@Foo("x")'                 | Q       || []
    }

    void "declared and inherited queries by name agree in the generated definition for #description"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition("${PKG}.Bar", source(annotations))
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        expect:
        probe(metadata, name) == [declared: expected, all: expected]

        where:
        description                        | annotations                 | name    || expected
        'a non-repeatable annotation'      | '@Foo("x")'                 | FOO     || ['x']
        'a repeatable written once'        | '@Q("a")'                   | Q       || ['a']
        'a repeatable written twice'       | '@Q("a") @Q("b")'           | Q       || ['a', 'b']
        'a container written by hand'      | '@Qs([Q("a"), Q("b")])'     | Q       || ['a', 'b']
        'a missing annotation'             | '@Foo("x")'                 | MISSING || []
        'a repeatable that is not present' | '@Foo("x")'                 | Q       || []
    }

    private static Map<String, List<String>> probe(AnnotationMetadata metadata, String name) {
        [
                declared: metadata.getDeclaredAnnotationValuesByName(name).collect { it.stringValue().get() },
                all     : metadata.getAnnotationValuesByName(name).collect { it.stringValue().get() },
        ]
    }
}
