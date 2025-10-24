package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * A field element returning data from a Python {@link AttributeDef}.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonFieldElement extends AbstractPythonElement implements FieldElement {
    private final PythonProcessingEnvironment environment;
    private final PythonClassElement owningType;
    private final ClassElement type;

    public PythonFieldElement(AttributeDef attributeDef,
                              PythonProcessingEnvironment environment,
                              PythonClassElement owningType,
                              ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(attributeDef, "AttributeDef cannot be null").name(),
            attributeDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");
        this.type = resolveType(attributeDef);
    }

    @Override
    public AttributeDef getNativeType() {
        return (AttributeDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return getType(); // Python doesn't have generics in the same way
    }

    @Override
    public Object getConstantValue() {
        AttributeDef attr = getNativeType();
        if (attr.value() != null) {
            // Try to convert Python literals to Java types
            return convertPythonValueToJava(attr.value());
        }
        return null;
    }

    @Override
    public boolean isStatic() {
        return getNativeType().isStatic();
    }

    @Override
    public boolean isFinal() {
        // Check if annotated with typing.Final
        return hasStereotype("typing.Final") || hasStereotype("Final");
    }

    @Override
    public ClassElement getDeclaringType() {
        return owningType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    private ClassElement resolveType(AttributeDef attributeDef) {
        if (attributeDef.annotation() != null) {
            // Try to resolve the type annotation
            String annotation = attributeDef.annotation();
            // Handle typing.Annotated specially
            if (annotation.startsWith("Annotated[") || annotation.startsWith("typing.Annotated[")) {
                // Extract the actual type from Annotated[type, metadata]
                int startIdx = annotation.indexOf('[');
                int endIdx = annotation.lastIndexOf(',');
                if (startIdx > 0 && endIdx > startIdx) {
                    String actualType = annotation.substring(startIdx + 1, endIdx).trim();
                    return GraalPyUtil.resolvePythonTypeToJava(actualType, environment.visitorContext());
                }
            }
            return GraalPyUtil.resolvePythonTypeToJava(annotation, environment.visitorContext());
        }
        // Infer from value if no annotation
        if (attributeDef.value() != null) {
            return inferTypeFromValue(attributeDef.value());
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private ClassElement inferTypeFromValue(Value value) {
        if (value == null) {
            return environment.visitorContext().getClassElement(Object.class).orElse(null);
        }
        Object javaValue = GraalPyUtil.convertValueToJava(value);
        if (javaValue instanceof Integer) {
            return environment.visitorContext().getClassElement(int.class).orElse(null);
        } else if (javaValue instanceof Double || javaValue instanceof Float) {
            return environment.visitorContext().getClassElement(double.class).orElse(null);
        } else if (javaValue instanceof String) {
            return environment.visitorContext().getClassElement(String.class).orElse(null);
        } else if (javaValue instanceof Boolean) {
            return environment.visitorContext().getClassElement(boolean.class).orElse(null);
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private Object convertPythonValueToJava(Value pythonValue) {
        if (pythonValue == null) {
            return null;
        }
        return GraalPyUtil.convertValueToJava(pythonValue);
    }
}
