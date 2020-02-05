/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Represents an injection point for a method that requires reflection.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ReflectionMethodInjectionPoint extends DefaultMethodInjectionPoint {

    /**
     * @param declaringBean      The declaring bean
     * @param declaringType      The declaring type
     * @param methodName         The method name
     * @param arguments          The arguments
     * @param annotationMetadata The annotation metadata
     */
    ReflectionMethodInjectionPoint(
        BeanDefinition declaringBean,
        Class<?> declaringType,
        String methodName,
        @Nullable Argument[] arguments,
        @Nullable AnnotationMetadata annotationMetadata) {
        super(declaringBean, declaringType, methodName, arguments, annotationMetadata);
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + declaringBean.getBeanType() + "] defines method [" + methodName + "] that requires the use of reflection to inject");
        }
    }

    @Override
    public boolean requiresReflection() {
        return true;
    }
}
