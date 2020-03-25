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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MutableHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpHeaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Delegates to Netty's {@link io.netty.handler.codec.http.HttpHeaders}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpHeaders implements MutableHttpHeaders {

    io.netty.handler.codec.http.HttpHeaders nettyHeaders;
    final ConversionService<?> conversionService;

    /**
     * @param nettyHeaders      The Netty Http headers
     * @param conversionService The conversion service
     */
    public NettyHttpHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders, ConversionService conversionService) {
        this.nettyHeaders = nettyHeaders;
        this.conversionService = conversionService;
    }

    /**
     * Default constructor.
     */
    public NettyHttpHeaders() {
        this.nettyHeaders = new DefaultHttpHeaders();
        this.conversionService = ConversionService.SHARED;
    }

    /**
     * @return The underlying Netty headers.
     */
    public io.netty.handler.codec.http.HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }

    /**
     * Sets the underlying netty headers.
     *
     * @param headers The Netty http headers
     */
    void setNettyHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        this.nettyHeaders = headers;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        List<String> values = nettyHeaders.getAll(name);
        if (values.size() > 0) {
            if (values.size() == 1 || !isCollectionOrArray(conversionContext.getArgument().getType())) {
                return conversionService.convert(values.get(0), conversionContext);
            } else {
                return conversionService.convert(values, conversionContext);
            }
        }
        return Optional.empty();
    }

    private boolean isCollectionOrArray(Class<?> clazz) {
        return clazz.isArray() || Collection.class.isAssignableFrom(clazz);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return nettyHeaders.getAll(name);
    }

    @Override
    public Set<String> names() {
        return nettyHeaders.names();
    }

    @Override
    public Collection<List<String>> values() {
        Set<String> names = names();
        List<List<String>> values = new ArrayList<>();
        for (String name : names) {
            values.add(getAll(name));
        }
        return Collections.unmodifiableList(values);
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

    @Override
    public MutableHeaders set(CharSequence header, CharSequence value) {
        nettyHeaders.set(header, value);
        return this;
    }

    @Override
    public MutableHttpHeaders remove(CharSequence header) {
        nettyHeaders.remove(header);
        return this;
    }
}
