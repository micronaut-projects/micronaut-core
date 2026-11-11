package io.micronaut.inject.qualifiers

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil

/**
 * The members of an annotation that are {@code @NonBinding} are read from the compilation's view of the
 * annotation interface, so a member a visitor marks non-binding while the compilation runs is recorded on the
 * occurrences read afterwards.
 *
 * @see NonBindingMarkingVisitor
 */
class NonBindingMemberAnnotatedByVisitorSpec extends AbstractBeanDefinitionSpec {

    void 'test a member a visitor annotates @NonBinding is recorded on the occurrences read afterwards'() {
        given:
        def definition = buildBeanDefinition('nonbindingvisitor.Test', '''
package nonbindingvisitor

import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@MyQualifier(member = "x")
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier {
    String member() default ""

    int bound() default 0
}
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
