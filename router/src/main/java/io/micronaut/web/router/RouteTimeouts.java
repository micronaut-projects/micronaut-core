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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.annotation.RequestTimeout;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Resolves the request timeout of a route, see {@link RouteInfo#getRequestTimeout()}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteTimeouts {

    /**
     * Marks a route without a timeout of its own in a cache.
     */
    static final Object NONE = new Object();

    private RouteTimeouts() {
    }

    /**
     * @param route The route
     * @return The timeout of the route, {@link Duration#ZERO} when disabled, or {@code null}
     */
    static @Nullable Duration resolve(RouteInfo<?> route) {
        Object attribute = route.getAttributes().get(RouteInfo.REQUEST_TIMEOUT_ATTRIBUTE);
        if (attribute != null) {
            return normalize(toDuration(attribute));
        }
        String value = route.getAnnotationMetadata().stringValue(RequestTimeout.class).orElse(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalize(toDuration(value.trim()));
    }

    private static Duration toDuration(Object value) {
        if (value instanceof Duration duration) {
            return duration;
        }
        String text = value.toString();
        if (text.equals("0") || text.startsWith("-")) {
            return Duration.ZERO;
        }
        return ConversionService.SHARED.convert(text, Duration.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid request timeout: " + text));
    }

    private static Duration normalize(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
