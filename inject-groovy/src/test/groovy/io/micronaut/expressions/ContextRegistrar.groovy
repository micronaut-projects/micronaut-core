package io.micronaut.expressions

import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class ContextRegistrar implements TypeElementVisitor<Object, Object> {
    static final List<String> CLASSES = ["test.Context"]

    static void setClasses(String...classes) {
        CLASSES.clear()
        CLASSES.addAll(classes)
    }

    static void reset() {
        CLASSES.clear()
        CLASSES.add("test.Context")
    }

    @Override
    void start(VisitorContext visitorContext) {
        for (cls in CLASSES) {
            visitorContext.getClassElement(cls).ifPresent {
                visitorContext.expressionCompilationContextFactory.registerContextClass(it)
            }
        }
    }
}
