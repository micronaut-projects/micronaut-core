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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a {@link Publisher} to a {@link Mono}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Mono.class)
public class PublisherToMonoConverter implements TypeConverter<Publisher, Mono> {

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Mono> convert(Publisher object, Class<Mono> targetType, ConversionContext context) {
        return Optional.of(Mono.from(object));
    }
}
