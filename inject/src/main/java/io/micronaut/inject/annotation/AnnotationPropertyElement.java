package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.MethodElement;

import java.util.Objects;

/**
 * Used to represent bean properties referenced in annotations at
 * compilation time.
 *
 * @author Sergey Gavrilov
 * @since 3.4.0
 */
@Internal
public final class AnnotationPropertyElement {
    private final AnnotationClassValue<?> owningType;
    private final String propertyName;
    private final MethodElement propertyGetter;

    /**
     * Constructs annotation property element.
     *
     * @param owningType the type owning referenced bean property
     * @param propertyName the name of bean property
     * @param propertyGetter the method used to access bean property
     */
    public AnnotationPropertyElement(AnnotationClassValue<?> owningType,
                                     String propertyName,
                                     MethodElement propertyGetter) {
        this.owningType = owningType;
        this.propertyName = propertyName;
        this.propertyGetter = propertyGetter;
    }

    /**
     * @return bean property name.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @return method used to access bean property.
     */
    public MethodElement getPropertyGetter() {
        return propertyGetter;
    }

    /**
     * @return the type of bean owning the property.
     */
    public AnnotationClassValue<?> getOwningType() {
        return owningType;
    }

    @Override
    public String toString() {
        return propertyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationPropertyElement that = (AnnotationPropertyElement) o;
        return Objects.equals(owningType, that.owningType) &&
                   Objects.equals(propertyName, that.propertyName) &&
                   Objects.equals(propertyGetter, that.propertyGetter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owningType, propertyName, propertyGetter);
    }
}
