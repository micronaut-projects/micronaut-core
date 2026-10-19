package io.micronaut.kotlin.processing.qualifiers

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationUtil

/**
 * The members of an annotation that are {@code @NonBinding} are read from the compilation's view of the
 * annotation class, so a member a visitor marks non-binding while the compilation runs is recorded on the
 * occurrences read afterwards.
 *
 * @see NonBindingMarkingVisitor
 */
class NonBindingMemberAnnotatedByVisitorSpec extends AbstractKotlinCompilerSpec {

    void 'test a member a visitor annotates @NonBinding is recorded on the occurrences read afterwards'() {
        given:
        def definition = buildBeanDefinition('nonbindingvisitor.Test', '''
package nonbindingvisitor

import jakarta.inject.Qualifier
import jakarta.inject.Singleton

@MyQualifier(member = "x")
@Singleton
class Test

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MyQualifier(
    val member: String = "",
    val bound: Int = 0
)
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingvisitor.MyQualifier')) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    private static Set<String> nonBinding(def annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set
    }
}
