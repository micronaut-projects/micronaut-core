package io.micronaut.python.processing.visitor;

import java.util.List;

public record AnnotationMemberDef(String name) implements ElementDef {
    @Override
    public List<DecoratorDef> decorators() {
        return List.of();
    }
}
