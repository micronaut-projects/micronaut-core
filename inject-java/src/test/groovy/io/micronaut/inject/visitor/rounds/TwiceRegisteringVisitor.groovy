package io.micronaut.inject.visitor.rounds

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Registers a bean for the class {@code duplicate.Twice} in the first round and again in the second, which the source
 * it generates in the first round starts, as a visitor that visits a class again in a later round would.
 */
class TwiceRegisteringVisitor implements TypeElementVisitor<Object, Object> {

    private static final String TWICE = 'duplicate.Twice'

    private boolean visited
    private int registrations

    @Override
    VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING
    }

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.name == TWICE && !visited) {
            visited = true
            context.visitGeneratedSourceFile('duplicate', 'Later', element).orElseThrow().write { Writer writer ->
                writer.write('package duplicate;\n\npublic class Later {\n}\n')
            }
        }
    }

    @Override
    void finishRound(VisitorContext context) {
        if (visited && registrations < 2) {
            registrations++
            ClassElement beanType = context.getRequiredClassElement(RegisteredRecord.name, context.elementAnnotationMetadataFactory)
            context.registerBean(beanType, context.getClassElement(TWICE).orElseThrow())
                    .qualifier("round$registrations")
        }
    }
}
