/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;

/**
 * This is a netty-specific reactor-like API for streaming bytes. It's a bit better than reactor
 * because it's more explicit about reference counting semantics, has more fine-grained controls
 * for cancelling, and has more relaxed concurrency semantics.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Internal
public interface ByteBufConsumer extends BufferConsumer {
    /**
     * Consume a buffer. Release ownership is transferred to this consumer.
     *
     * @param buf The buffer to consume
     */
    void add(@NonNull ByteBuf buf);
}
