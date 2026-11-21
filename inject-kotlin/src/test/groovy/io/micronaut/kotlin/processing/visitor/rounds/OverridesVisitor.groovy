package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Looks up the methods the methods of the classes of the package {@code overrides} override at the end of every
 * round, the last one included, so that the lookups are cached, and again, on the classes looked up anew, once the
 * compilation is finished.
 */
class OverridesVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * The methods each method overrides, as looked up once the compilation is finished.
     */
    static final List<String> OVERRIDDEN = new CopyOnWriteArrayList<>()

    private final List<String> classes = []

    @Override
    VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING
    }

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.packageName == 'overrides') {
            classes.add(element.name)
        }
    }

    @Override
    void finishRound(VisitorContext context) {
        overridden(context)
    }

    @Override
    void finishCompilation(VisitorContext context) {
        OVERRIDDEN.addAll(overridden(context))
        classes.clear()
    }

    private List<String> overridden(VisitorContext context) {
        List<String> result = []
        for (String name : classes) {
            ClassElement classElement = context.getClassElement(name).orElseThrow()
            for (MethodElement method : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
                List<String> overridden = method.overriddenMethods.collect { it.declaringType.name + '.' + it.name + ':' + it.returnType.name }
                result.add(name + '.' + method.name + ' -> ' + overridden)
            }
        }
        return result
    }
}
