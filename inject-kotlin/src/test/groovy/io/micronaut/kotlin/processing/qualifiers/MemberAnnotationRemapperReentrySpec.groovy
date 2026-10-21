package io.micronaut.kotlin.processing.qualifiers

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

/**
 * A remapper, mapper or transformer reading the metadata of an annotation's members while an occurrence
 * of the annotation is built re-enters the build of a member whose annotations name the annotation again.
 * The build of an element already in progress answers rather than starting over.
 *
 * @see MemberReadingAnnotationRemapper
 */
class MemberAnnotationRemapperReentrySpec extends AbstractKotlinCompilerSpec {

    void 'test a remapper reading the members of two annotations annotated with each other'() {
        given:
        def definition = buildBeanDefinition('remapreentry.Test', '''
package remapreentry

import jakarta.inject.Singleton

@A(v = "a")
@Singleton
class Test

@Retention(AnnotationRetention.RUNTIME)
annotation class A(
    @get:B(v = "on a")
    val v: String = ""
)

@Retention(AnnotationRetention.RUNTIME)
annotation class B(
    @get:A(v = "on b")
    val v: String = ""
)
''')

        expect:
        definition.hasAnnotation('remapreentry.A')
        definition.getAnnotation('remapreentry.A').stringValue('v').get() == 'a'
    }
}
