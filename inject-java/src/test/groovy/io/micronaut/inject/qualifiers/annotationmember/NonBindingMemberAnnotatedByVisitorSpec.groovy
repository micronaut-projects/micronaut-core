package io.micronaut.inject.qualifiers.annotationmember

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.NonBinding
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * The members of an annotation that are {@code @NonBinding} are read from the compilation's view of the
 * annotation interface, so a member a visitor marks non-binding while the compilation runs is recorded on the
 * occurrences read afterwards.
 */
class NonBindingMemberAnnotatedByVisitorSpec extends AbstractTypeElementSpec {

    void 'test a member a visitor annotates @NonBinding is recorded on the occurrences read afterwards'() {
        given:
        def definition = buildBeanDefinition('nonbindingvisitor.Test', '''
package nonbindingvisitor;

import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@MyQualifier(member = "x")
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier {
    String member() default "";

    int bound() default 0;
}
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingvisitor.MyQualifier')) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new NonBindingMarkingVisitor()]
    }

    private static Set<String> nonBinding(def annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set
    }

    /**
     * Marks the {@code member} member of {@code nonbindingvisitor.MyQualifier} non-binding before any class is
     * visited, the way a build compatible extension's discovery phase does.
     */
    static class NonBindingMarkingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement('nonbindingvisitor.MyQualifier').ifPresent { type ->
                def member = (type as AnnotationElement).getMembers().find { it.name == 'member' }
                member.annotate(NonBinding)
            }
        }
    }
}
