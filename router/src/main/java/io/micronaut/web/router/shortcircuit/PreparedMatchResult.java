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
package io.micronaut.web.router.shortcircuit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.web.router.UriRouteInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents the result of a {@link io.micronaut.web.router.Router} match operation. It
 * is shared between multiple requests with the same parameters. However, it includes additional
 * request information that is not present in {@link UriRouteInfo}, such as the request path and
 * certain headers.
 */
@Internal
public final class PreparedMatchResult {
    private final MatchRule rule;
    private final UriRouteInfo<?, ?> routeInfo;

    private FastEntry<?> fastEntry = new FastEntry<>(null, null);

    private final Map<HandlerKey<?>, Object> multipleHandlers = Collections.synchronizedMap(new HashMap<>());

    public PreparedMatchResult(MatchRule rule, UriRouteInfo<?, ?> routeInfo) {
        this.rule = rule;
        this.routeInfo = routeInfo;
    }

    public MatchRule getRule() {
        return rule;
    }

    public UriRouteInfo<?, ?> getRouteInfo() {
        return routeInfo;
    }

    /**
     * Get a handler that has been previously set using {@link #setHandler}.
     *
     * @param key The handler key
     * @return The handler, or {@code null if unset}
     * @param <H> The handler type
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <H> H getHandler(@NonNull HandlerKey<H> key) {
        FastEntry<?> fastEntry = this.fastEntry;
        Object handler;
        if (fastEntry.key == key) {
            handler = fastEntry.value;
        } else {
            handler = multipleHandlers.get(key);
        }
        return (H) handler;
    }

    /**
     * Set a handler for future retrieval with {@link #getHandler}.
     *
     * @param key The handler key
     * @param handler The handler
     * @param <H> The handler type
     */
    public <H> void setHandler(@NonNull HandlerKey<H> key, @NonNull H handler) {
        multipleHandlers.put(key, handler);
        fastEntry = new FastEntry<>(key, handler);
    }

    private record FastEntry<H>(HandlerKey<H> key, H value) {
    }

    @SuppressWarnings("unused")
    public static final class HandlerKey<H> {
    }
}
