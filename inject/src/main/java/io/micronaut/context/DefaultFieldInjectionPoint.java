/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.FieldInjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final BeanDefinition declaringComponent;
    private final boolean requiresReflection;

    private Argument<T> argument = null;

    DefaultFieldInjectionPoint(BeanDefinition declaringComponent, Field field, Annotation qualifier, boolean requiresReflection) {
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
    public BeanDefinition getDeclaringBean() {
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
            throw new BeanInstantiationException("Exception occured injecting field [" + field + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Argument<T> asArgument() {
        if (argument == null) {
            Class[] typeArguments = GenericTypeUtils.resolveGenericTypeArguments(field);
            List<Argument<?>> arguments = Collections.emptyList();
            if (typeArguments != null && typeArguments.length > 0) {
                TypeVariable<? extends Class<?>>[] typeParameters = field.getType().getTypeParameters();
                if (typeParameters.length == typeArguments.length) {
                    arguments = new ArrayList<>();
                    for (int i = 0; i < typeParameters.length; i++) {
                        TypeVariable<? extends Class<?>> typeParameter = typeParameters[i];
                        String name = typeParameter.getName();
                        arguments.add(Argument.of(typeArguments[i], name));
                    }
                }
            }
            argument = Argument.of(field, field.getName(), null, arguments.toArray(new Argument[arguments.size()]));
        }
        return argument;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return field.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return field.getDeclaredAnnotations();
    }

    @Override
    public String toString() {
        return field.toString();
    }
}
