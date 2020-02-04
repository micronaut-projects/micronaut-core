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
import io.micronaut.inject.ConstructorInjectionPoint;

import javax.annotation.Nullable;

/**
 * <p>Calls a method that constructs the object.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ReflectionMethodConstructorInjectionPoint extends ReflectionMethodInjectionPoint implements ConstructorInjectionPoint {

    /**
     * @param declaringBean      The declaring bean
     * @param declaringType      The declaring type
     * @param methodName         The method name
     * @param arguments          The arguments
     * @param annotationMetadata The annotation metadata
     */
    ReflectionMethodConstructorInjectionPoint(
        BeanDefinition declaringBean,
        Class<?> declaringType,
        String methodName,
        @Nullable Argument[] arguments,
        @Nullable AnnotationMetadata annotationMetadata) {

        super(declaringBean, declaringType, methodName, arguments, annotationMetadata);
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + declaringBean.getBeanType() + "] defines constructor [" + methodName + "] that requires the use of reflection to inject");
        }
    }

    @Override
    public Object invoke(Object... args) {
        throw new UnsupportedOperationException("Use MethodInjectionPoint#invoke(..) instead");
    }
}
