/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collection;
import java.util.Optional;

/**
 * <p>A {@link BeanCreatedEventListener} that will monitor the creation of {@link ExecutableMethodProcessor} instances
 * and call {@link io.micronaut.context.processor.AnnotationProcessor#process(BeanDefinition, Object)} for each
 * available {@link ExecutableMethod}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecutableMethodProcessorListener implements BeanCreatedEventListener<ExecutableMethodProcessor> {

    @Override
    public ExecutableMethodProcessor onCreated(BeanCreatedEvent<ExecutableMethodProcessor> event) {
        ExecutableMethodProcessor processor = event.getBean();
        BeanContext beanContext = event.getSource();
        Optional<Class> targetAnnotation = GenericTypeUtils.resolveInterfaceTypeArgument(processor.getClass(), ExecutableMethodProcessor.class);
        if (targetAnnotation.isPresent()) {
            Class annotationType = targetAnnotation.get();
            Collection<BeanDefinition<?>> beanDefinitions = beanContext.getBeanDefinitions(Qualifiers.byStereotype(annotationType));
            for (BeanDefinition<?> beanDefinition : beanDefinitions) {
                Collection<? extends ExecutableMethod<?, ?>> executableMethods = beanDefinition.getExecutableMethods();
                for (ExecutableMethod<?, ?> executableMethod : executableMethods) {
                    processor.process(beanDefinition, executableMethod);
                }
            }
        }

        if (processor instanceof Completable) {
            ((Completable) processor).onComplete();
        }

        return processor;
    }
}
