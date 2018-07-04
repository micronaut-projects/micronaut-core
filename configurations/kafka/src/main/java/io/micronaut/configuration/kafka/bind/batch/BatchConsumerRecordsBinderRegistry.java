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

package io.micronaut.configuration.kafka.bind.batch;

import io.micronaut.configuration.kafka.bind.ConsumerRecordBinderRegistry;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binds arguments in batches from a {@link ConsumerRecords} instance.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class BatchConsumerRecordsBinderRegistry implements ArgumentBinderRegistry<ConsumerRecords<?, ?>> {

    private final ConsumerRecordBinderRegistry consumerRecordBinderRegistry;

    /**
     * Constructs a new instance.
     *
     * @param consumerRecordBinderRegistry The wrapped {@link ConsumerRecordBinderRegistry}
     */
    public BatchConsumerRecordsBinderRegistry(ConsumerRecordBinderRegistry consumerRecordBinderRegistry) {
        this.consumerRecordBinderRegistry = consumerRecordBinderRegistry;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<ArgumentBinder<T, ConsumerRecords<?, ?>>> findArgumentBinder(Argument<T> argument, ConsumerRecords<?, ?> source) {
        Class<T> argType = argument.getType();
        if (Iterable.class.isAssignableFrom(argType) || argType.isArray() || Publishers.isConvertibleToPublisher(argType)) {
            Argument<?> batchType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            List bound = new ArrayList();

            return Optional.of((context, consumerRecords) -> {
                for (ConsumerRecord<?, ?> consumerRecord : consumerRecords) {
                    Optional<ArgumentBinder<?, ConsumerRecord<?, ?>>> binder = consumerRecordBinderRegistry.findArgumentBinder((Argument) argument, consumerRecord);
                    binder.ifPresent(b -> {
                        Argument<?> newArg = Argument.of(batchType.getType(), argument.getName(), argument.getAnnotationMetadata(), batchType.getTypeParameters());
                        ArgumentConversionContext conversionContext = ConversionContext.of(newArg);
                        ArgumentBinder.BindingResult<?> result = b.bind(
                                conversionContext,
                                consumerRecord);
                        if (result.isPresentAndSatisfied()) {
                            bound.add(result.get());
                        }

                    });
                }
                return () -> ConversionService.SHARED.convert(bound, argument);
            });
        }
        return Optional.empty();
    }
}
