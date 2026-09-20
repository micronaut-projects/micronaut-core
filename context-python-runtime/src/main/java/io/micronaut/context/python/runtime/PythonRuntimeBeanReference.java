/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

/**
 * The lightweight reference of a runtime-generated bean: enough for candidate selection, from the saved model, and
 * nothing generated until the definition is loaded. Conditions are evaluated by the loaded definition, in its
 * context, exactly as a build-time definition evaluates them after its pre-check.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeBeanReference implements BeanDefinitionReference<Object> {

    private final Class<?> owner;
    private final Class<Object> beanType;
    private final BeanDefinitionModel definition;

    @SuppressWarnings("unchecked")
    PythonRuntimeBeanReference(Class<?> owner, Class<?> beanType, BeanDefinitionModel definition) {
        this.owner = owner;
        this.beanType = (Class<Object>) beanType;
        this.definition = definition;
    }

    @Override
    public String getBeanDefinitionName() {
        return definition.definitionClassName();
    }

    @Override
    public BeanDefinition<Object> load() {
        return PythonRuntimeMetadata.definition(owner, definition.definitionClassName()).load();
    }

    @Override
    public BeanDefinition<Object> load(BeanContext context) {
        return PythonRuntimeMetadata.definition(owner, definition.definitionClassName()).load(context);
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return definition.info().isSingleton();
    }

    @Override
    public boolean isContextScope() {
        return "io.micronaut.context.annotation.Context".equals(definition.info().scope());
    }

    @Override
    public boolean isConfigurationProperties() {
        return definition.info().isConfigurationProperties();
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        // Like a build-time reference: every condition is checked by the definition once loaded, not by the reference.
        return true;
    }

    @Override
    public Class<Object> getBeanType() {
        return beanType;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return PythonRuntimeMetadata.definitionAnnotationMetadata(owner, definition);
    }

    @Override
    public String toString() {
        return "PythonRuntimeBeanReference(" + definition.definitionClassName() + ")";
    }
}
