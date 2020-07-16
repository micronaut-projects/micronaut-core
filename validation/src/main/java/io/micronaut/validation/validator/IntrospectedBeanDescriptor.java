/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.validator;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;

import javax.validation.*;
import javax.validation.metadata.*;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Basic implementation of {@link BeanDescriptor} that uses bean introspection metadata.
 *
 * @author graemerocher
 * @since 1.2.0
 */
@Internal
class IntrospectedBeanDescriptor implements BeanDescriptor, ElementDescriptor.ConstraintFinder {

    private final BeanIntrospection<?> beanIntrospection;

    /**
     * Default constructor.
     *
     * @param beanIntrospection The bean introspection
     */
    IntrospectedBeanDescriptor(BeanIntrospection<?> beanIntrospection) {
        ArgumentUtils.requireNonNull("beanIntrospection", beanIntrospection);
        this.beanIntrospection = beanIntrospection;
    }

    @Override
    public boolean isBeanConstrained() {
        return hasConstraints();
    }

    @Override
    public PropertyDescriptor getConstraintsForProperty(String propertyName) {
        return beanIntrospection.getProperty(propertyName)
                .map(IntrospectedPropertyDescriptor::new)
                .orElse(null);
    }

    @Override
    public Set<PropertyDescriptor> getConstrainedProperties() {
        return beanIntrospection.getIndexedProperties(Constraint.class)
                .stream()
                .map(IntrospectedPropertyDescriptor::new)
                .collect(Collectors.toSet());
    }

    @Override
    public MethodDescriptor getConstraintsForMethod(String methodName, Class<?>... parameterTypes) {
        return null;
    }

    @Override
    public Set<MethodDescriptor> getConstrainedMethods(MethodType methodType, MethodType... methodTypes) {
        return Collections.emptySet();
    }

    @Override
    public ConstructorDescriptor getConstraintsForConstructor(Class<?>... parameterTypes) {
        return null;
    }

    @Override
    public Set<ConstructorDescriptor> getConstrainedConstructors() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasConstraints() {
        return beanIntrospection.getIndexedProperty(Constraint.class).isPresent();
    }

    @Override
    public Class<?> getElementClass() {
        return beanIntrospection.getBeanType();
    }

    @Override
    public ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
        return this;
    }

    @Override
    public ConstraintFinder lookingAt(Scope scope) {
        return this;
    }

    @Override
    public ConstraintFinder declaredOn(ElementType... types) {
        return this;
    }

    @Override
    public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
        return Collections.emptySet();
    }

    @Override
    public ConstraintFinder findConstraints() {
        return this;
    }

    /**
     * Internal implementation of {@link PropertyDescriptor}.
     */
    private final class IntrospectedPropertyDescriptor implements PropertyDescriptor, ConstraintFinder {

        private final BeanProperty<?, ?> beanProperty;

        IntrospectedPropertyDescriptor(BeanProperty<?, ?> beanProperty) {
            this.beanProperty = beanProperty;
        }

        @Override
        public String getPropertyName() {
            return beanProperty.getName();
        }

        @Override
        public boolean isCascaded() {
            return beanProperty.hasAnnotation(Valid.class);
        }

        @Override
        public Set<GroupConversionDescriptor> getGroupConversions() {
            return Collections.emptySet();
        }

        @Override
        public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasConstraints() {
            return beanProperty.hasStereotype(Constraint.class);
        }

        @Override
        public Class<?> getElementClass() {
            return beanProperty.getType();
        }

        @Override
        public ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
            return this;
        }

        @Override
        public ConstraintFinder lookingAt(Scope scope) {
            return this;
        }

        @Override
        public ConstraintFinder declaredOn(ElementType... types) {
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
            return beanProperty.getAnnotationTypesByStereotype(Constraint.class)
                    .stream().map(type -> {
                        AnnotationValue<? extends Annotation> annotation = beanProperty.getAnnotation(type);
                        DefaultConstraintDescriptor<?> descriptor = new DefaultConstraintDescriptor(
                                beanProperty.getAnnotationMetadata(),
                                type,
                                annotation
                        );
                        return descriptor;
                    }).collect(Collectors.toSet());
        }

        @Override
        public ConstraintFinder findConstraints() {
            return this;
        }
    }
}
