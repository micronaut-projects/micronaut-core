/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethodsDefinition;

import java.util.Map;

/**
 * A variation of {@link AbstractInitializableBeanDefinition} that is also a {@link io.micronaut.inject.BeanDefinitionReference}.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 4.4.0
 */
public abstract class AbstractInitializableBeanDefinitionAndReference<T> extends AbstractInitializableBeanDefinition<T> implements BeanDefinitionReference<T> {

    protected AbstractInitializableBeanDefinitionAndReference(Class<T> beanType,
                                                              @Nullable MethodOrFieldReference constructor,
                                                              @Nullable AnnotationMetadata annotationMetadata,
                                                              @Nullable MethodReference[] methodInjection,
                                                              @Nullable FieldReference[] fieldInjection,
                                                              @Nullable AnnotationReference[] annotationInjection,
                                                              @Nullable ExecutableMethodsDefinition<T> executableMethodsDefinition,
                                                              @Nullable Map<String, Argument<?>[]> typeArgumentsMap,
                                                              @NonNull PrecalculatedInfo precalculatedInfo) {
        super(beanType, constructor, annotationMetadata, methodInjection, fieldInjection, annotationInjection, executableMethodsDefinition, typeArgumentsMap, precalculatedInfo);
    }


    /**
     * Represents {@link BeanDefinitionReference#getBeanDefinitionName()} when the class implements {@link BeanDefinitionReference}.
     *
     * @return The name of this bean definition
     */
    @Override
    public final String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public final BeanDefinition<T> load(BeanContext context) {
        BeanDefinition<T> definition = load();
        if (definition instanceof EnvironmentConfigurable environmentConfigurable) {
            if (context instanceof DefaultApplicationContext applicationContext) {
                // Performance optimization to check for the actual class to avoid the type-check pollution
                environmentConfigurable.configure(applicationContext.getEnvironment());
            } else if (context instanceof ApplicationContext applicationContext) {
                environmentConfigurable.configure(applicationContext.getEnvironment());
            }
        }
        if (definition instanceof BeanContextConfigurable ctxConfigurable) {
            ctxConfigurable.configure(context);
        }
        return definition;
    }

    /**
     * Method returns always true, otherwise class not found would eliminate the instance.
     *
     * @return always true
     */
    @Override
    public final boolean isPresent() {
        return true;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
