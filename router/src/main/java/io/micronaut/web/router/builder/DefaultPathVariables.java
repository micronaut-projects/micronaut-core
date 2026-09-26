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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.RoutePathVariables;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The {@link PathVariables} of a route match given to a handler function: its variable values,
 * converted with its conversion service, and the target a {@code DynamicRouteTarget} resolved for
 * the route, and the media type of the response its route selector negotiated.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultPathVariables extends RoutePathVariables {

    private final @Nullable Object resolvedTarget;
    private final @Nullable MediaType selectedMediaType;

    /**
     * @param values            The variable values, viewed read-only: the map of the match is
     *                          shared with the other arguments bound from it
     * @param conversionService The conversion service of the route
     * @param resolvedTarget    The target a {@code DynamicRouteTarget} resolved for the route, or {@code null}
     * @param selectedMediaType The media type of the response a route selector negotiated, or {@code null}
     */
    public DefaultPathVariables(Map<String, Object> values, ConversionService conversionService,
                                @Nullable Object resolvedTarget, @Nullable MediaType selectedMediaType) {
        super(values, conversionService);
        this.resolvedTarget = resolvedTarget;
        this.selectedMediaType = selectedMediaType;
    }

    /**
     * @param values            The variable values
     * @param conversionService The conversion service of the route
     * @param resolvedTarget    The target a {@code DynamicRouteTarget} resolved for the route, or {@code null}
     */
    public DefaultPathVariables(Map<String, Object> values, ConversionService conversionService, @Nullable Object resolvedTarget) {
        this(values, conversionService, resolvedTarget, null);
    }

    /**
     * @param values            The variable values
     * @param conversionService The conversion service of the route
     */
    public DefaultPathVariables(Map<String, Object> values, ConversionService conversionService) {
        this(values, conversionService, null);
    }

    /**
     * @return The target a {@code DynamicRouteTarget} resolved for the route, or {@code null}
     */
    public @Nullable Object resolvedTarget() {
        return resolvedTarget;
    }

    /**
     * @return The media type of the response a route selector negotiated, or {@code null}
     */
    public @Nullable MediaType selectedMediaType() {
        return selectedMediaType;
    }

    @Override
    public String toString() {
        Object target = resolvedTarget;
        return target == null ? super.toString() : super.toString() + " of " + target;
    }
}
