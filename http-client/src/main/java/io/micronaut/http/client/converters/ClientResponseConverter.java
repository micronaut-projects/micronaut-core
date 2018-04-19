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

package io.micronaut.http.client.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.client.FullNettyClientHttpResponse;
import io.micronaut.http.netty.NettyHttpResponse;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a response from the http client to a response processable
 * by the http server.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class ClientResponseConverter implements TypeConverter<FullNettyClientHttpResponse, NettyHttpResponse> {

    private final ConversionService conversionService;

    protected ClientResponseConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<NettyHttpResponse> convert(FullNettyClientHttpResponse object, Class<NettyHttpResponse> targetType, ConversionContext context) {
        return Optional.of(new NettyHttpResponse(object.getNativeResponse(), conversionService).body(object.getBody()));
    }
}
