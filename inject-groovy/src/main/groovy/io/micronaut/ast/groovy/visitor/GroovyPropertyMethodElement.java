package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.codehaus.groovy.ast.PropertyNode;

import javax.annotation.Nonnull;

/**
 * Represents a property element as a method element.
 *
 * @author graemerocher
 * @since 1.3.4
 */
@Internal
public class GroovyPropertyMethodElement implements MethodElement {
    private final PropertyNode propertyNode;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     * @param propertyNode The property element
     * @param annotationMetadata The annotation metadata
     */
    public GroovyPropertyMethodElement(PropertyNode propertyNode, AnnotationMetadata annotationMetadata) {
        this.propertyNode = propertyNode;
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Nonnull
    @Override
    public String getName() {
        return NameUtils.getterNameFor(propertyNode.getName());
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Nonnull
    @Override
    public Object getNativeType() {
        return propertyNode;
    }

    @Nonnull
    @Override
    public ClassElement getReturnType() {
        throw new UnsupportedOperationException("Cannot retrieve return type from here");
    }

    @Override
    public ParameterElement[] getParameters() {
        return ParameterElement.ZERO_PARAMETERS;
    }

    @Override
    public ClassElement getDeclaringType() {
        throw new UnsupportedOperationException("Cannot retrieve declaring type from here");
    }
}
