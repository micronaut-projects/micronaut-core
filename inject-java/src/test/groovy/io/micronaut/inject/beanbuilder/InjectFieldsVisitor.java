package io.micronaut.inject.beanbuilder;

import org.jspecify.annotations.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class InjectFieldsVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getSimpleName().equals("Test")) {
            context.getClassElement("injectbuilder.Holder")
                    .ifPresent(holder -> element
                            .addAssociatedBean(holder)
                            .qualifier("built")
                            .inject()
                    );
        }
    }

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
