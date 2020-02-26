/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Represents an injection point for a field.
 *
 * @param <B> The declaring bean type
 * @param <T> The field type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultFieldInjectionPoint<B, T> implements FieldInjectionPoint<B, T>, EnvironmentConfigurable {

    private final BeanDefinition declaringBean;
    private final Class declaringType;
    private final Class<T> fieldType;
    private final String field;
    private final AnnotationMetadata annotationMetadata;
    private final Argument[] typeArguments;
    private Environment environment;

    /**
     * @param declaringBean      The declaring bean
     * @param declaringType      The declaring type
     * @param fieldType          The field type
     * @param field              The name of the field
     * @param annotationMetadata The annotation metadata
     * @param typeArguments      the generic type arguments
     */
    DefaultFieldInjectionPoint(
        BeanDefinition declaringBean,
        Class declaringType,
        Class<T> fieldType,
        String field,
        @Nullable AnnotationMetadata annotationMetadata,
        @Nullable Argument[] typeArguments) {

        this.declaringBean = declaringBean;
        this.declaringType = declaringType;
        this.fieldType = fieldType;
        this.field = field;
        this.annotationMetadata = initAnnotationMetadata(annotationMetadata);
        this.typeArguments = ArrayUtils.isEmpty(typeArguments) ? Argument.ZERO_ARGUMENTS : typeArguments;
    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String toString() {
        return fieldType.getSimpleName() + " " + field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultFieldInjectionPoint<?, ?> that = (DefaultFieldInjectionPoint<?, ?>) o;
        return Objects.equals(declaringType, that.declaringType) &&
            Objects.equals(fieldType, that.fieldType) &&
            Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaringType, fieldType, field);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public String getName() {
        return field;
    }

    @Override
    public Field getField() {
        return ReflectionUtils.getRequiredField(declaringType, this.field);
    }

    @Override
    public Class<T> getType() {
        return fieldType;
    }

    @Override
    public void set(T instance, Object object) {
        Field field = getField();
        try {
            field.setAccessible(true);
            field.set(instance, object);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public Argument<T> asArgument() {
        return Argument.of(
            fieldType,
            field,
            annotationMetadata,
            typeArguments
        );
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return declaringBean;
    }

    @Override
    public boolean requiresReflection() {
        return false;
    }

    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
    }

    @Override
    public Annotation[] synthesizeAll() {
        return getAnnotationMetadata().synthesizeAll();
    }

    @Override
    public Annotation[] synthesizeDeclared() {
        return getAnnotationMetadata().synthesizeDeclared();
    }

    private AnnotationMetadata initAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            return new FieldAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
        } else if (annotationMetadata != null) {
            return annotationMetadata;
        }
        return AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class FieldAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        FieldAnnotationMetadata(DefaultAnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }
}
