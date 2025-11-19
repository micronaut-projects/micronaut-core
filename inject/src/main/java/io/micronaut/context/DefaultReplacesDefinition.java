/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ReplacesDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Represents the default implementation of the {@link ReplacesDefinition} interface, designed to define rules for replacing
 * existing bean definitions within a bean context.
 *
 * @param beanType          The type of current bean definition.
 * @param beanTypeToReplace The type of the bean being replaced.
 * @param qualifier         The qualifier of the bean being replaced.
 * @param factoryClass      The factory class of the bean being replaced.
 * @param <T>               The type of the bean being replaced.
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public record DefaultReplacesDefinition<T>(@NonNull Class<T> beanType,
                                           @Nullable Class<T> beanTypeToReplace,
                                           @Nullable Qualifier<T> qualifier,
                                           @Nullable Class<?> factoryClass) implements ReplacesDefinition<T> {


    public DefaultReplacesDefinition(@NonNull Class<T> beanType, AnnotationValue<Replaces> replacesAnnotation) {
        this(
            beanType,
            (Class<T>) replacesAnnotation.classValue(Replaces.MEMBER_BEAN).orElse(beanType),
            findQualifier(replacesAnnotation),
            replacesAnnotation.classValue(Replaces.MEMBER_FACTORY).orElse(null)
        );
    }

    @Nullable
    private static <K> Qualifier<K> findQualifier(@NonNull AnnotationValue<Replaces> replacesAnnotation) {
        String named = replacesAnnotation.stringValue(Replaces.MEMBER_NAMED).orElse(null);
        AnnotationClassValue<?> qualifierClassValue = replacesAnnotation.annotationClassValue(Replaces.MEMBER_QUALIFIER).orElse(null);
        if (named != null && qualifierClassValue != null) {
            throw new ConfigurationException("Both \"named\" and \"qualifier\" should not be present: " + replacesAnnotation);
        }
        if (named != null) {
            return Qualifiers.byName(named);
        }
        if (qualifierClassValue != null) {
            @SuppressWarnings("unchecked") final Class<? extends Annotation> qualifierClass =
                (Class<? extends Annotation>) qualifierClassValue.getType().orElse(null);
            if (qualifierClass != null && !qualifierClass.isAssignableFrom(Annotation.class)) {
                return Qualifiers.byStereotype(qualifierClass);
            } else {
                throw new ConfigurationException("Not accessible qualifier: " + qualifierClassValue.getName());
            }
        }
        return null;
    }

    @Override
    public boolean replaces(BeanDefinition<T> beanDefinition) {
        if (qualifier != null) {
            if (qualifier.doesQualify(beanType, beanDefinition)) {
                if (DefaultBeanContext.LOG.isDebugEnabled()) {
                    DefaultBeanContext.LOG.debug("Bean [{}] replaces existing bean of type [{}] qualified by qualifier [{}]", beanDefinition, beanDefinition.getBeanType(), qualifier);
                }
                return true;
            }
            return false;
        }

        if (factoryClass != null) {
            Optional<Class<?>> declaringType = beanDefinition.getDeclaringType();
            if (declaringType.isPresent()) {
                if (factoryClass == declaringType.get()) {
                    if (DefaultBeanContext.LOG.isDebugEnabled()) {
                        if (beanTypeToReplace == null) {
                            DefaultBeanContext.LOG.debug("Bean [{}] replaces the factory type [{}]", beanType, factoryClass);
                        } else {
                            DefaultBeanContext.LOG.debug("Bean [{}] replaces existing bean of type [{}] in factory type [{}]", beanType, beanTypeToReplace, factoryClass);
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        if (beanTypeToReplace != null) {
            final boolean isTypeMatches = checkIfTypeMatches(beanDefinition, beanTypeToReplace);
            if (isTypeMatches && DefaultBeanContext.LOG.isDebugEnabled()) {
                DefaultBeanContext.LOG.debug("Bean [{}] replaces existing bean of type [{}]", beanType, beanTypeToReplace);
            }
            return isTypeMatches;
        }
        return false; // No more replacement definition to check
    }

    private boolean checkIfTypeMatches(BeanDefinition<T> definitionToBeReplaced,
                                       Class<T> beanTypeToReplace) {
        Class<T> bt = definitionToBeReplaced.getBeanType();
        Class<?> defaultImplementation = definitionToBeReplaced.getDefaultImplementation();
        if (defaultImplementation != null) {
            if (defaultImplementation == bt) {
                return beanTypeToReplace.isAssignableFrom(bt);
            } else {
                return beanTypeToReplace == bt;
            }
        }
        return beanTypeToReplace != Object.class && beanTypeToReplace.isAssignableFrom(bt);
    }
}
