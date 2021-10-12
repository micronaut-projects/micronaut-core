/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.runtime.converters.reactive;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import org.reactivestreams.Publisher;
import jakarta.inject.Singleton;

/**
 * Registers converters for Reactive types such as {@link Publisher}.
 *
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Singleton
@Requires(classes = Publishers.class)
public class ReactiveTypeConverterRegistrar implements TypeConverterRegistrar {

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(Object.class, Publisher.class, obj -> {
            if (obj instanceof Publisher) {
                return (Publisher) obj;
            } else {
                return Publishers.just(obj);
            }
        });
    }
}
