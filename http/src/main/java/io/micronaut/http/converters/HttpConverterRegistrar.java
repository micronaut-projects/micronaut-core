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
package io.micronaut.http.converters;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;

import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

/**
 * Converter registrar for HTTP classes.
 *
 * @author graemerocher
 * @since 2.0
 */
@Singleton
public class HttpConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                MediaType.class,
                (object, targetType, context) -> {
                    try {
                        return Optional.of(MediaType.of(object));
                    } catch (IllegalArgumentException e) {
                        context.reject(e);
                        return Optional.empty();
                    }
                }
        );
        conversionService.addConverter(
                Number.class,
                HttpStatus.class,
                (object, targetType, context) -> {
                    try {
                        HttpStatus status = HttpStatus.valueOf(object.shortValue());
                        return Optional.of(status);
                    } catch (IllegalArgumentException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );
        conversionService.addConverter(
                CharSequence.class,
                SocketAddress.class,
                (object, targetType, context) -> {
                    String[] parts = object.toString().split(":");
                    if (parts.length == 2) {
                        int port = Integer.valueOf(parts[1]);
                        return Optional.of(new InetSocketAddress(parts[0], port));
                    } else {
                        return Optional.empty();
                    }
                }
        );
    }
}
