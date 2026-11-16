/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.websocket;

import io.micronaut.websocket.CloseReason;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared close state of the handler and session, including closes before the handler is added.
 */
final class CloseState {
    static final CloseState OPEN = new CloseState(null);
    static final CloseState CLOSED = new CloseState(null);
    private static final AttributeKey<AtomicReference<CloseState>> KEY = AttributeKey.valueOf(CloseState.class, "closeState");

    @Nullable
    final CloseReason reason;

    CloseState(@Nullable CloseReason reason) {
        this.reason = reason;
    }

    static AtomicReference<CloseState> get(Channel channel) {
        Attribute<AtomicReference<CloseState>> attribute = channel.attr(KEY);
        AtomicReference<CloseState> state = attribute.get();
        if (state == null) {
            AtomicReference<CloseState> created = new AtomicReference<>(OPEN);
            state = attribute.setIfAbsent(created);
            if (state == null) {
                state = created;
            }
        }
        return state;
    }
}
