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
package io.micronaut.http.netty.body;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.CustomizableJsonHandler;
import io.micronaut.json.body.JsonStreamMessageHandler;
import jakarta.inject.Singleton;

/**
 * Compatibility bean delegating JSON streams to json-core.
 *
 * @param <T> The body type
 * @deprecated Use {@link JsonStreamMessageHandler} instead.
 * @since 4.0.0
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@Internal
@Singleton
@Replaces(JsonStreamMessageHandler.class)
@Produces(MediaType.APPLICATION_JSON_STREAM)
@Consumes(MediaType.APPLICATION_JSON_STREAM)
public final class NettyJsonStreamHandler<T> extends DelegatingJsonHandler<T, JsonStreamMessageHandler<T>> {

    /** @param jsonMapper The JSON mapper */
    public NettyJsonStreamHandler(JsonMapper jsonMapper) {
        this(new JsonStreamMessageHandler<>(jsonMapper, NettyForeignBufferReleaser.INSTANCE));
    }

    private NettyJsonStreamHandler(JsonStreamMessageHandler<T> delegate) {
        super(delegate);
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures features) {
        return new NettyJsonStreamHandler<>((JsonStreamMessageHandler<T>) delegate.customize(features));
    }

    @Override
    public NettyJsonStreamHandler<T> createSpecific(Argument<T> type) {
        return new NettyJsonStreamHandler<>(delegate.createSpecific(type));
    }

    @Override
    public NettyJsonStreamHandler<T> createSpecificReader(Argument<T> type) {
        return createSpecific(type);
    }
}
