/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.WritableBodyWriter;
import io.micronaut.http.client.AbstractHttpClientFactory;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientFactory;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.micronaut.runtime.ApplicationConfiguration;

import java.net.URI;

/**
 * Factory to create {@literal java.net.http.*} HTTP Clients.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public class JdkHttpClientFactory extends AbstractHttpClientFactory<DefaultJdkHttpClient> implements RawHttpClientFactory {

    public JdkHttpClientFactory() {
        super(null, createDefaultMessageBodyHandlerRegistry(), ConversionService.SHARED);
    }

    @Override
    protected DefaultJdkHttpClient createHttpClient(URI uri) {
        return new DefaultJdkHttpClient(uri, conversionService);
    }

    @Override
    protected DefaultJdkHttpClient createHttpClient(URI uri, HttpClientConfiguration configuration) {
        return new DefaultJdkHttpClient(uri, configuration, mediaTypeCodecRegistry, messageBodyHandlerRegistry, conversionService);
    }

    public static MessageBodyHandlerRegistry createDefaultMessageBodyHandlerRegistry() {
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(
            applicationConfiguration,
            ByteArrayBufferFactory.INSTANCE,
            new WritableBodyWriter(applicationConfiguration)
        );
        JsonMapper mapper = JsonMapper.createDefault();
        registry.add(MediaType.APPLICATION_JSON_TYPE, new JsonMessageHandler<>(mapper));
        registry.add(MediaType.APPLICATION_JSON_STREAM_TYPE, new JsonMessageHandler<>(mapper));
        return registry;
    }

    @Override
    public @NonNull RawHttpClient createRawClient(@Nullable URI url, @NonNull HttpClientConfiguration configuration) {
        return new JdkRawHttpClient(createHttpClient(url, configuration));
    }
}
