package example.micronaut;

import com.blazebit.persistence.spi.JpqlFunctionKind;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class AddAnnotationWithEnumVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
            // Simulate annotating with enum value
            // Micronaut should store the string value and not the enum value in the metadata
        element.annotate("Something", builder -> builder.value(JpqlFunctionKind.DETERMINISTIC));
    }
}
