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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.CustomizableJsonHandler;
import io.micronaut.json.body.JsonMessageHandler;
import jakarta.inject.Singleton;

/**
 * Compatibility bean for applications replacing the former Netty JSON handler.
 *
 * @param <T> The body type
 * @deprecated JSON streaming is provided by {@link JsonMessageHandler}. Existing custom Netty
 * handlers should continue replacing this type until it is removed.
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@Internal
@Singleton
@Order(JsonMessageHandler.ORDER)
@Replaces(JsonMessageHandler.class)
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@BootstrapContextCompatible
@Requires(beans = JsonMapper.class)
public final class NettyJsonHandler<T> extends DelegatingJsonHandler<T, JsonMessageHandler<T>> {

    /** @param jsonMapper The JSON mapper */
    public NettyJsonHandler(JsonMapper jsonMapper) {
        this(new JsonMessageHandler<>(jsonMapper, NettyForeignBufferReleaser.INSTANCE));
    }

    private NettyJsonHandler(JsonMessageHandler<T> delegate) {
        super(delegate);
    }

    @Override
    public CustomizableJsonHandler customize(JsonFeatures features) {
        return new NettyJsonHandler<>((JsonMessageHandler<T>) delegate.customize(features));
    }

    @Override
    public NettyJsonHandler<T> createSpecific(Argument<T> type) {
        return new NettyJsonHandler<>(delegate.createSpecific(type));
    }

    @Override
    public NettyJsonHandler<T> createSpecificReader(Argument<T> type) {
        return createSpecific(type);
    }
}
