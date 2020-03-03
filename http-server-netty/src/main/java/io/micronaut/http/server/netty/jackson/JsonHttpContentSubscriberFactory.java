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
package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;

import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Builds the {@link org.reactivestreams.Subscriber} for JSON requests.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Consumes({MediaType.APPLICATION_JSON_STREAM, MediaType.APPLICATION_JSON})
@Singleton
@Internal
public class JsonHttpContentSubscriberFactory implements HttpContentSubscriberFactory {

    private final HttpServerConfiguration httpServerConfiguration;
    private final @Nullable JsonFactory jsonFactory;
    private final DeserializationConfig deserializationConfig;

    /**
     * @param objectMapper The jackson object mapper.
     * @param httpServerConfiguration The Http server configuration
     * @param jsonFactory             The json factory
     */
    public JsonHttpContentSubscriberFactory(
            ObjectMapper objectMapper,
            HttpServerConfiguration httpServerConfiguration,
            @Nullable JsonFactory jsonFactory) {
        ArgumentUtils.requireNonNull("objectMapper", objectMapper);
        this.httpServerConfiguration = httpServerConfiguration;
        this.jsonFactory = jsonFactory;
        this.deserializationConfig = objectMapper.getDeserializationConfig();
    }

    @Override
    public HttpContentProcessor build(NettyHttpRequest request) {
        return new JsonContentProcessor(request, httpServerConfiguration, jsonFactory, deserializationConfig);
    }
}
