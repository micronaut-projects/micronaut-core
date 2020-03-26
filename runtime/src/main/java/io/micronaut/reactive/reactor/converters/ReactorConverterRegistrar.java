/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.reactive.reactor.converters;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converter registrar for Reactor.
 *
 * @author graemerocher
 * @since 2.0
 */
@Singleton
@Internal
@Requires(classes = Flux.class)
class ReactorConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                Mono.class,
                Maybe.class,
                (TypeConverter<Mono, Maybe>) (object, targetType, context) -> Optional.of(Flowable.fromPublisher(object).firstElement())
        );
        conversionService.addConverter(
                Publisher.class,
                Flux.class,
                (TypeConverter<Publisher, Flux>) (object, targetType, context) -> Optional.of(Flux.from(object))
        );
        conversionService.addConverter(
                Publisher.class,
                Mono.class,
                (TypeConverter<Publisher, Mono>) (object, targetType, context) -> Optional.of(Mono.from(object))
        );
        conversionService.addConverter(
                Object.class,
                Flux.class,
                (TypeConverter<Object, Flux>) (object, targetType, context) -> Optional.of(Flux.just(object))
        );
        conversionService.addConverter(
                Object.class,
                Mono.class,
                (TypeConverter<Object, Mono>) (object, targetType, context) -> Optional.of(Mono.just(object))
        );
    }
}
