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

package io.micronaut.configuration.kafka.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A registry of {@link ConsumerRecordBinder}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Singleton
public class ConsumerRecordBinderRegistry implements ArgumentBinderRegistry<ConsumerRecord<?, ?>> {

    private final Map<Class<? extends Annotation>, ConsumerRecordBinder<?>> byAnnotation = new HashMap<>();
    private final Map<Integer, ConsumerRecordBinder<?>> byType = new HashMap<>();

    /**
     * Creates the registry for the given binders.
     *
     * @param binders The binders
     */
    public ConsumerRecordBinderRegistry(ConsumerRecordBinder<?>... binders) {
        if (ArrayUtils.isNotEmpty(binders)) {
            for (ConsumerRecordBinder<?> binder : binders) {
                if (binder instanceof AnnotatedConsumerRecordBinder) {
                    AnnotatedConsumerRecordBinder<?, ?> annotatedConsumerRecordBinder = (AnnotatedConsumerRecordBinder<?, ?>) binder;
                    byAnnotation.put(
                            annotatedConsumerRecordBinder.annotationType(),
                            annotatedConsumerRecordBinder
                    );
                } else if (binder instanceof TypedConsumerRecordBinder) {
                    TypedConsumerRecordBinder typedConsumerRecordBinder = (TypedConsumerRecordBinder) binder;
                    byType.put(
                            typedConsumerRecordBinder.argumentType().typeHashCode(),
                            typedConsumerRecordBinder
                    );
                }
            }
        }
    }

    @Override
    public <T> Optional<ArgumentBinder<T, ConsumerRecord<?, ?>>> findArgumentBinder(Argument<T> argument, ConsumerRecord<?, ?> source) {
        Optional<Class<? extends Annotation>> annotationType = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (annotationType.isPresent()) {
            @SuppressWarnings("unchecked") ConsumerRecordBinder<T> consumerRecordBinder =
                    (ConsumerRecordBinder<T>) byAnnotation.get(annotationType.get());

            return Optional.ofNullable(consumerRecordBinder);
        } else {
            @SuppressWarnings("unchecked")
            ConsumerRecordBinder<T> binder = (ConsumerRecordBinder<T>) byType.get(argument.typeHashCode());
            if (binder != null) {
                return Optional.of(binder);
            } else {
                return Optional.of(new KafkaDefaultBinder<>());
            }
        }
    }
}
