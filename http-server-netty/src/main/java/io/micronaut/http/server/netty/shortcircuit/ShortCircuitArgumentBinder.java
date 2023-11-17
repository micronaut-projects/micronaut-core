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
package io.micronaut.http.server.netty.shortcircuit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import io.micronaut.web.router.shortcircuit.MatchRule;
import io.netty.handler.codec.http.HttpRequest;

import java.util.Optional;

/**
 * {@link RequestArgumentBinder} extension for fast routing.
 *
 * @param <T>
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
public interface ShortCircuitArgumentBinder<T> extends RequestArgumentBinder<T> {
    /**
     * Prepare this binder for a parameter.
     *
     * @param argument         The parameter type with annotations
     * @param fixedContentType The content type of the request, if known
     * @return The prepared binder or {@link Optional#empty()} if this argument cannot use short-circuit binding
     */
    Optional<Prepared> prepare(@NonNull Argument<T> argument, @Nullable MatchRule.ContentType fixedContentType);

    /**
     * Prepared argument binder.
     */
    interface Prepared {
        /**
         * Bind the parameter.
         *
         * @param nettyRequest The netty request
         * @param mnHeaders    The request headers (micronaut-http class)
         * @param body         The request body
         * @return The bound argument
         */
        Object bind(@NonNull HttpRequest nettyRequest, HttpHeaders mnHeaders, @NonNull ImmediateByteBody body);
    }
}
