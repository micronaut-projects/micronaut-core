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
import io.netty.handler.codec.http.HttpRequest;

/**
 * This class matches a set of routes against their match rules and returns either a successful
 * match or an indeterminate result. This is the hot path of fast routing.
 *
 * @param <R> The route type
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
public interface MatchPlan<R> {
    /**
     * Attempt to match the given request using this match plan.
     *
     * @param request The request to match
     * @return The match result
     */
    @NonNull
    ExecutionLeaf<R> execute(@NonNull HttpRequest request);
}
