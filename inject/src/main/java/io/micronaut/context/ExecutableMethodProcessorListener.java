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

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collection;
import java.util.List;

/**
 * Implements the legacy behaviour of {@link ExecutableMethodProcessor}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@NextMajorVersion("Remove in v6")
@Internal
final class ExecutableMethodProcessorListener implements BeanCreatedEventListener<ExecutableMethodProcessor<?>> {

    @Override
    public ExecutableMethodProcessor<?> onCreated(BeanCreatedEvent<ExecutableMethodProcessor<?>> event) {
        AnnotationValue<Deprecated> deprecatedAnnotation = event.getBeanDefinition().getAnnotation(Deprecated.class);
        if (deprecatedAnnotation == null) {
            return event.getBean();
        }
        deprecatedAnnotation.stringValue().ifPresent(message -> DefaultBeanContext.LOG.warn("{}: {}", event.getBeanDefinition().getBeanType().getName(), message));
        ExecutableMethodProcessor<?> processor = event.getBean();
        BeanDefinition<ExecutableMethodProcessor<?>> processorDefinition = event.getBeanDefinition();
        BeanContext beanContext = event.getSource();
        if (processor instanceof LifeCycle<?> cycle) {
            try {
                cycle.start();
            } catch (Exception e) {
                throw new BeanContextException("Error starting bean processing: " + e.getMessage(), e);
            }
        }
        final List<Argument<?>> typeArguments = processorDefinition.getTypeArguments(ExecutableMethodProcessor.class);
        if (typeArguments.size() == 1) {
            final Argument<?> annotation = typeArguments.get(0);
            Collection<BeanDefinition<Object>> beanDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype((Class) annotation.getType()));

            for (BeanDefinition<Object> beanDefinition : beanDefinitions) {
                for (ExecutableMethod<Object, ?> executableMethod : beanDefinition.getExecutableMethods()) {
                    try {
                        processor.process(beanDefinition, executableMethod);
                    } catch (Exception e) {
                        throw new BeanContextException("Error processing bean definition [" + beanDefinition + "]: " + e.getMessage(), e);
                    }
                }
            }
        }
        if (processor instanceof LifeCycle<?> cycle) {
            try {
                cycle.stop();
            } catch (Exception e) {
                throw new BeanContextException("Error finalizing bean processing: " + e.getMessage(), e);
            }
        }
        return processor;
    }
}
