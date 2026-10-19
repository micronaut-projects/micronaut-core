package io.micronaut.inject.qualifiers

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

/**
 * A remapper, mapper or transformer reading the metadata of an annotation's members while an occurrence
 * of the annotation is built re-enters the build of a member whose annotations name the annotation again.
 * The build of an element already in progress answers rather than starting over.
 *
 * @see MemberReadingAnnotationRemapper
 */
class MemberAnnotationRemapperReentrySpec extends AbstractBeanDefinitionSpec {

    void 'test a remapper reading the members of two annotations annotated with each other'() {
        given:
        def definition = buildBeanDefinition('remapreentry.Test', '''
package remapreentry

import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@A(v = "a")
@Singleton
class Test {
}

@Retention(RUNTIME)
@interface A {
    @B(v = "on a")
    String v() default ""
}

@Retention(RUNTIME)
@interface B {
    @A(v = "on b")
    String v() default ""
}
''')

        expect:
        definition.hasAnnotation('remapreentry.A')
        definition.getAnnotation('remapreentry.A').stringValue('v').get() == 'a'
    }
}
