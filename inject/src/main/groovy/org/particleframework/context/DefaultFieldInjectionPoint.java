package org.particleframework.context;

import org.particleframework.inject.ComponentDefinition;
import org.particleframework.inject.FieldInjectionPoint;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Represents an injection point for a field
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultFieldInjectionPoint<T> implements FieldInjectionPoint<T> {
    private final String name;
    private final Field field;
    private final Annotation qualifier;
    private final ComponentDefinition declaringComponent;
    private final boolean requiresReflection;

    DefaultFieldInjectionPoint(ComponentDefinition declaringComponent, Field field, Annotation qualifier, boolean requiresReflection) {
        this.field = field;
        this.field.setAccessible(true);
        this.name = field.getName();
        this.declaringComponent = declaringComponent;
        this.qualifier = qualifier;
        this.requiresReflection = requiresReflection;
    }

    @Override
    public Annotation getQualifier() {
        return this.qualifier;
    }

    @Override
    public boolean requiresReflection() {
        return requiresReflection;
    }

    @Override
    public ComponentDefinition getDeclaringComponent() {
        return this.declaringComponent;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Field getField() {
        return this.field;
    }

    @Override
    public Class<T> getType() {
        return (Class<T>) this.field.getType();
    }

    @Override
    public void set(Object object, T instance) {
        try {
            field.set(object, instance);
        } catch (IllegalAccessException e) {
            throw new BeanInstantiationException("Exception occured injecting field ["+field+"]: " + e.getMessage(), e);
        }
    }
}
