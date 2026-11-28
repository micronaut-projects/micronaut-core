/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.netty.converters;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.Unpooled;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyByteBufConverterRegistrarTest {

    @Test
    void convertsByteBufThroughStringConverterWithoutServerRuntime() {
        try (ApplicationContext context = ApplicationContext.builder()
            .singletons(new StringToValueConverter())
            .start()) {
            Optional<Value> converted = context.getBean(ConversionService.class)
                .convert(Unpooled.copiedBuffer("hello", UTF_8), Value.class);

            assertTrue(converted.isPresent());
            assertEquals("hello", converted.get().value());
        }
    }

    record Value(String value) {
    }

    @Singleton
    static final class StringToValueConverter implements TypeConverter<String, Value> {
        @Override
        public Optional<Value> convert(String object, Class<Value> targetType, ConversionContext context) {
            return Optional.of(new Value(object));
        }
    }
}
