package io.micronaut.python.processing.visitor;

import java.util.List;

public sealed interface ElementDef permits AnnotationMemberDef, AttributeDef, ClassDef, FunctionDef {
    default List<DecoratorDef> decorators() {
        return List.of();
    }

    String name();
}
