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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.shortcircuit.MatchRule;

import java.util.Optional;

/**
 * Route with a request predicate.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface RequestMatcher {

    /**
     * Match the given request.
     *
     * @param httpRequest The request
     * @return true if route matches this request
     */
    boolean matching(HttpRequest<?> httpRequest);

    /**
     * Get a {@link MatchRule} that is equivalent to {@link #matching(HttpRequest)}.
     *
     * @return The equivalent rule or {@link Optional#empty()} if this matcher cannot be expressed
     * as a rule
     * @since 4.3.0
     */
    @Internal
    default Optional<MatchRule> matchingRule() {
        return Optional.empty();
    }

}
