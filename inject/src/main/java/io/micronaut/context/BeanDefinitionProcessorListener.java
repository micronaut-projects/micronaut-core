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
package io.micronaut.context;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collection;
import java.util.List;

/**
 * <p>A {@link BeanCreatedEventListener} that will monitor the creation of {@link BeanDefinitionProcessor} instances
 * and call {@link io.micronaut.context.processor.BeanDefinitionProcessor#process(BeanDefinition, BeanContext)} for each
 * available bean annotated with the given annotation type of {@link BeanDefinitionProcessor}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class BeanDefinitionProcessorListener implements BeanCreatedEventListener<BeanDefinitionProcessor<?>> {

    @Override
    public BeanDefinitionProcessor<?> onCreated(BeanCreatedEvent<BeanDefinitionProcessor<?>> event) {
        BeanDefinitionProcessor<?> beanDefinitionProcessor = event.getBean();
        BeanDefinition<BeanDefinitionProcessor<?>> processorDefinition = event.getBeanDefinition();
        BeanContext beanContext = event.getSource();
        if (beanDefinitionProcessor instanceof LifeCycle<?> cycle) {
            try {
                cycle.start();
            } catch (Exception e) {
                throw new BeanContextException("Error starting bean processing: " + e.getMessage(), e);
            }
        }
        final List<Argument<?>> typeArguments = processorDefinition.getTypeArguments(BeanDefinitionProcessor.class);
        if (typeArguments.size() == 1) {
            final Argument<?> annotation = typeArguments.get(0);
            Collection<BeanDefinition<?>> beanDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype((Class) annotation.getType()));

            for (BeanDefinition<?> beanDefinition : beanDefinitions) {
                try {
                    beanDefinitionProcessor.process(beanDefinition, beanContext);
                } catch (Exception e) {
                    throw new BeanContextException(
                        "Error processing bean definition [" + beanDefinition + "]: " + e.getMessage(),
                        e
                    );
                }
            }
        }
        if (beanDefinitionProcessor instanceof LifeCycle<?> cycle) {
            try {
                cycle.stop();
            } catch (Exception e) {
                throw new BeanContextException("Error finalizing bean processing: " + e.getMessage(), e);
            }
        }
        return beanDefinitionProcessor;
    }
}
