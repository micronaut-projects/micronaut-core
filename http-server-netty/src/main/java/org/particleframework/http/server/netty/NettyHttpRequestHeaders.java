/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.MutableHttpHeaders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Delegates to Netty's {@link io.netty.handler.codec.http.HttpHeaders}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpRequestHeaders implements MutableHttpHeaders {
    final io.netty.handler.codec.http.HttpHeaders nettyHeaders;
    final ConversionService conversionService;

    NettyHttpRequestHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders, ConversionService conversionService) {
        this.nettyHeaders = nettyHeaders;
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        String value = nettyHeaders.get(name);
        if (value != null) {
            return conversionService.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        String value = nettyHeaders.get(name);
        if (value != null) {
            return conversionService.convert(value, requiredType.getType(), ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return nettyHeaders.getAll(name);
    }

    @Override
    public Set<String> getNames() {
        return nettyHeaders.names();
    }

    @Override
    public String get(CharSequence name) {
        return nettyHeaders.get(name);
    }

    @Override
    public MutableHttpHeaders add(CharSequence header, CharSequence value) {
        nettyHeaders.add(header, value);
        return this;
    }
}
