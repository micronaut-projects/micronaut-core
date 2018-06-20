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

package io.micronaut.configuration.kafka;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.messaging.MessageHeaders;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * A {@link MessageHeaders} implementation for Kafka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class KafkaHeaders implements MessageHeaders {

    private final Headers headers;

    /**
     * Constructs a new instance for the given headers.
     *
     * @param headers The kafka headers
     */
    public KafkaHeaders(Headers headers) {
        Objects.requireNonNull(headers, "Argument [headers] cannot be null");
        this.headers = headers;
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return null;
    }

    @Override
    public String get(CharSequence name) {
        Header header = headers.lastHeader(name.toString());
        if (header != null) {
            return new String(header.value());
        }
        return null;
    }

    @Override
    public Set<String> names() {
        return Arrays.stream(headers.toArray()).map(Header::key).collect(Collectors.toSet());
    }

    @Override
    public Collection<List<String>> values() {
        return names().stream().map(name -> {
            Iterable<Header> headers = KafkaHeaders.this.headers.headers(name);
            List<String> values = new ArrayList<>();
            for (Header header : headers) {
                values.add(new String(header.value()));
            }
            return values;
        }).collect(Collectors.toList());
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String v = get(name);
        if (v != null) {
            return ConversionService.SHARED.convert(v, conversionContext);
        }
        return Optional.empty();
    }
}
