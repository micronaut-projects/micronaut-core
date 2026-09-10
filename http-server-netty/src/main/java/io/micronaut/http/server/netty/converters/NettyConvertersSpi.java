/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty.converters;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.CharSequenceToEnumConverter;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.logging.LogLevel;

import java.util.Map;
import java.util.Optional;

/**
 * Factory for bytebuf related converters that do not need the application context (can be
 * registered with SPI).
 *
 * @author Jonas Konrad
 * @since 3.6.3
 */
@Internal
public final class NettyConvertersSpi implements TypeConverterRegistrar {
    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(CharSequence.class, LogLevel.class, new CharSequenceToEnumConverter<>());
        conversionService.addConverter(
                CharSequence.class,
                NettyHttpServerConfiguration.NettyListenerConfiguration.Family.class,
                new CharSequenceToEnumConverter<>()
        );

        conversionService.addConverter(
                PartData.class,
                byte[].class,
                (upload, targetType, context) -> Optional.of(upload.getBytes())
        );

        conversionService.addConverter(
                Map.class,
                WriteBufferWaterMark.class,
                (map, targetType, context) -> {
                    Object h = map.get("high");
                    Object l = map.get("low");
                    if (h != null && l != null) {
                        try {
                            int high = Integer.parseInt(h.toString());
                            int low = Integer.parseInt(l.toString());
                            return Optional.of(new WriteBufferWaterMark(low, high));
                        } catch (NumberFormatException e) {
                            context.reject(e);
                            return Optional.empty();
                        }
                    }
                    return Optional.empty();
                }
        );
    }

}
