package io.micronaut.python.annotation.processing.test.qualifiers

import io.micronaut.context.annotation.NonBinding
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * The members of an annotation that are {@code @NonBinding} are read from the compilation's view of the
 * annotation declaration, so a member a visitor marks non-binding while the compilation runs is recorded on the
 * occurrences read afterwards.
 *
 * <p>The Python compilation reads the metadata of every class to generate its Java stub before the visitors
 * start, so the occurrence read afterwards is one a visitor adds.</p>
 */
class NonBindingMemberAnnotatedByVisitorSpec extends AbstractPythonTypeElementSpec {

    void 'test a member a visitor annotates @NonBinding is recorded on the occurrences read afterwards'() {
        given:
        def definition = buildBeanDefinition('python', 'NonBindingTest', '''
from jakarta.inject import Qualifier
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@Qualifier
@micronaut_annotation("nonbindingvisitor.MyQualifier")
def my_qualifier(member: str = "", bound: int = 0):
    def decorator(target):
        return target
    return decorator

@Singleton
class NonBindingTest:
    pass
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingvisitor.MyQualifier')) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['member', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    private static Set<String> nonBinding(def annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set
    }

    /**
     * Marks the {@code member} member of {@code nonbindingvisitor.MyQualifier} non-binding, the way a build
     * compatible extension's discovery phase does, and then qualifies {@code python.NonBindingTest} with it.
     */
    static class NonBindingMarkingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext visitorContext) {
            if (element.getName() != 'python.NonBindingTest') {
                return
            }
            def qualifier = visitorContext.getClassElement('nonbindingvisitor.MyQualifier').get() as AnnotationElement
            qualifier.getMembers().find { it.name == 'member' }.annotate(NonBinding)
            element.annotate('nonbindingvisitor.MyQualifier') { it.member('member', 'x') }
        }
    }
}
