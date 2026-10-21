package io.micronaut.inject.qualifiers

import io.micronaut.context.annotation.NonBinding
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Marks the {@code member} member of {@code nonbindingvisitor.MyQualifier} non-binding before any class is
 * visited, the way a build compatible extension's discovery phase does.
 *
 * @see NonBindingMemberAnnotatedByVisitorSpec
 */
class NonBindingMarkingVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    void start(VisitorContext visitorContext) {
        visitorContext.getClassElement('nonbindingvisitor.MyQualifier').ifPresent { type ->
            def member = (type as AnnotationElement).getMembers().find { it.name == 'member' }
            member.annotate(NonBinding)
        }
    }
}
