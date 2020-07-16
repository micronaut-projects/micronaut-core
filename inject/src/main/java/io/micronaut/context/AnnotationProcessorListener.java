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

import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.processor.AnnotationProcessor;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * <p>A {@link BeanCreatedEventListener} that will monitor the creation of {@link ExecutableMethodProcessor} instances
 * and call {@link io.micronaut.context.processor.AnnotationProcessor#process(BeanDefinition, Object)} for each
 * available {@link ExecutableMethod}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationProcessorListener implements BeanCreatedEventListener<AnnotationProcessor> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);

    @Override
    public AnnotationProcessor onCreated(BeanCreatedEvent<AnnotationProcessor> event) {
        AnnotationProcessor processor = event.getBean();
        BeanDefinition<AnnotationProcessor> processorDefinition = event.getBeanDefinition();
        BeanContext beanContext = event.getSource();
        if (processor instanceof ExecutableMethodProcessor) {

            List<Argument<?>> typeArguments = processorDefinition.getTypeArguments(ExecutableMethodProcessor.class);
            if (!typeArguments.isEmpty()) {
                final Argument<?> firstArgument = typeArguments.get(0);
                Collection<BeanDefinition<?>> beanDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype((Class) firstArgument.getType()));

                boolean isParallel = firstArgument.isAnnotationPresent(Parallel.class);

                if (isParallel) {
                    for (BeanDefinition<?> beanDefinition : beanDefinitions) {
                        Collection<? extends ExecutableMethod<?, ?>> executableMethods = beanDefinition.getExecutableMethods();
                        for (ExecutableMethod<?, ?> executableMethod : executableMethods) {
                            ForkJoinPool.commonPool().execute(() -> {
                                        try {
                                            if (beanContext.isRunning()) {
                                                processor.process(beanDefinition, executableMethod);
                                            }
                                        } catch (Throwable e) {
                                            if (LOG.isErrorEnabled()) {
                                                LOG.error("Error processing bean method " + beanDefinition + "." + executableMethod + " with processor (" + processor + "): " + e.getMessage(), e);
                                            }
                                            Boolean shutdownOnError = executableMethod.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                                            if (shutdownOnError) {
                                                beanContext.stop();
                                            }
                                        }
                                    }
                            );
                        }
                    }
                } else {
                    for (BeanDefinition<?> beanDefinition : beanDefinitions) {
                        Collection<? extends ExecutableMethod<?, ?>> executableMethods = beanDefinition.getExecutableMethods();
                        for (ExecutableMethod<?, ?> executableMethod : executableMethods) {
                            try {
                                processor.process(beanDefinition, executableMethod);
                            } catch (Exception e) {
                                throw new BeanContextException(
                                        "Error processing bean [" + beanDefinition + "] method definition [" + executableMethod + "]: " + e.getMessage(),
                                        e
                                );
                            }
                        }
                    }
                }
            }
        } else if (processor instanceof BeanDefinitionProcessor) {
            BeanDefinitionProcessor beanDefinitionProcessor = (BeanDefinitionProcessor) processor;
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
        }

        if (processor instanceof Completable) {
            try {
                ((Completable) processor).onComplete();
            } catch (Exception e) {
                throw new BeanContextException("Error finalizing bean processing: " + e.getMessage(), e);
            }
        }

        return processor;
    }
}
