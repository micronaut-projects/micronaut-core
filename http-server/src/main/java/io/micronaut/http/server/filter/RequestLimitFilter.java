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
package io.micronaut.http.server.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.cors.CorsFilter;
import io.micronaut.http.server.limit.Acquisition;
import io.micronaut.http.server.limit.RequestLimit;
import io.micronaut.http.server.limit.RequestLimitExceededException;
import io.micronaut.http.server.limit.RequestLimiter;
import io.micronaut.http.server.limit.RequestLimiterConfiguration;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

/**
 * Applies the {@link RequestLimiter request limiters} to the requests: those whose configured
 * patterns match the path, then those that the matched route selects with {@link RequestLimit}.
 * A permit is held until the response is done, see
 * {@link ServerHttpRequest#whenResponseCompleted}; a server that cannot tell releases it when the
 * response leaves the filters. A rejection releases the permits acquired before it and throws a
 * {@link RequestLimitExceededException}.
 *
 * <p>Only present when a limiter exists. It runs after the CORS filter, so that a rejection still
 * carries the CORS headers.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Requires(beans = RequestLimiter.class)
@ServerFilter(MATCH_ALL_PATTERN)
@Internal
public final class RequestLimitFilter implements Ordered {

    /**
     * The order of the filter.
     */
    public static final int ORDER = CorsFilter.CORS_FILTER_ORDER + 20;

    private static final String PERMITS_ATTRIBUTE = "micronaut.server.request-limit.permits";

    private final Map<String, RequestLimiter> limiters = new HashMap<>();
    private final List<PatternLimiter> patternLimiters = new ArrayList<>();

    /**
     * @param limiters       The limiters
     * @param configurations The configurations of the configured limiters
     */
    RequestLimitFilter(List<RequestLimiter> limiters, List<RequestLimiterConfiguration> configurations) {
        for (RequestLimiter limiter : limiters) {
            this.limiters.put(limiter.getName(), limiter);
        }
        for (RequestLimiterConfiguration configuration : configurations) {
            RequestLimiter limiter = this.limiters.get(configuration.getName());
            if (limiter != null && !configuration.getPatterns().isEmpty()) {
                patternLimiters.add(new PatternLimiter(configuration.getPatterns(), limiter));
            }
        }
    }

    /**
     * Acquire the permits of the request.
     *
     * @param request The request
     */
    @RequestFilter
    @Internal
    public void filterRequest(HttpRequest<?> request) {
        Set<RequestLimiter> selected = select(request);
        if (selected.isEmpty()) {
            return;
        }
        List<Acquisition.Permit> permits = new ArrayList<>(selected.size());
        for (RequestLimiter limiter : selected) {
            Acquisition acquisition = limiter.tryAcquire(request);
            if (acquisition instanceof Acquisition.Rejected rejected) {
                releaseAll(permits);
                throw new RequestLimitExceededException(limiter.getName(), rejected.status(), rejected.retryAfter());
            }
            permits.add((Acquisition.Permit) acquisition);
        }
        if (!(request instanceof ServerHttpRequest<?> serverRequest) || !serverRequest.whenResponseCompleted(failure -> releaseAll(permits))) {
            // the server cannot tell when the response is done: release when it leaves the filters
            request.setAttribute(PERMITS_ATTRIBUTE, permits);
        }
    }

    /**
     * Release the permits of a server that cannot tell when the response is done.
     *
     * @param request  The request
     * @param response The response
     */
    @ResponseFilter
    @Internal
    @SuppressWarnings("unchecked")
    public void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        request.getAttribute(PERMITS_ATTRIBUTE).ifPresent(permits -> {
            request.removeAttribute(PERMITS_ATTRIBUTE, Object.class);
            releaseAll((List<Acquisition.Permit>) permits);
        });
    }

    private Set<RequestLimiter> select(HttpRequest<?> request) {
        Set<RequestLimiter> selected = new LinkedHashSet<>();
        if (!patternLimiters.isEmpty()) {
            String path = request.getPath();
            for (PatternLimiter patternLimiter : patternLimiters) {
                if (patternLimiter.matches(path)) {
                    selected.add(patternLimiter.limiter());
                }
            }
        }
        RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(request).orElse(null);
        if (routeMatch != null) {
            for (String name : routeMatch.getAnnotationMetadata().stringValues(RequestLimit.class)) {
                selected.add(limiter(name));
            }
            Object attribute = routeMatch.getRouteInfo().getAttribute(RequestLimit.ATTRIBUTE).orElse(null);
            if (attribute instanceof CharSequence name) {
                selected.add(limiter(name.toString()));
            } else if (attribute instanceof String[] names) {
                for (String name : names) {
                    selected.add(limiter(name));
                }
            } else if (attribute instanceof Collection<?> names) {
                for (Object name : names) {
                    selected.add(limiter(String.valueOf(name)));
                }
            }
        }
        return selected;
    }

    private RequestLimiter limiter(String name) {
        RequestLimiter limiter = limiters.get(name);
        if (limiter == null) {
            throw new IllegalStateException("No request limiter named [" + name + "]. Known limiters: " + limiters.keySet());
        }
        return limiter;
    }

    private static void releaseAll(List<Acquisition.Permit> permits) {
        for (Acquisition.Permit permit : permits) {
            permit.release();
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private record PatternLimiter(List<String> patterns, RequestLimiter limiter) {
        boolean matches(@Nullable String path) {
            if (path == null) {
                return false;
            }
            for (String pattern : patterns) {
                if (PathMatcher.ANT.matches(pattern, path)) {
                    return true;
                }
            }
            return false;
        }
    }
}
