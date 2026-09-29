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
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Which of the filters of a {@link DefaultRouter} that apply to some requests only apply to the
 * requests of one URI route, decided once for the route where the route decides it.
 *
 * <p>Whether a filter applies depends on the route (a {@link io.micronaut.http.annotation.FilterMatcher}
 * annotation), on the HTTP method and on the path. The method of a request that matched the route
 * is the method of the route. Its path starts with the literal start of the route template, or is
 * the template itself when it has no variables, which often decides a filter pattern too: the
 * route {@code /api/books/{id}} always matches {@code /api/**} and never {@code /admin/**}. The
 * plan is used for a request only if its method and path are consistent with the route, so a
 * decision never depends on the route having matched the request. The filters it cannot decide
 * are matched for each request as before.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteFilterPlan {

    static final byte MATCH = 0;
    static final byte NO_MATCH = 1;
    static final byte CHECK = 2;

    final DefaultRouter owner;
    private final HttpMethod method;
    /**
     * The template when it has no variables, otherwise {@code null}.
     */
    private final @Nullable String literalTemplate;
    /**
     * The start of the template before its first variable.
     */
    private final String literalPrefix;
    /**
     * Per filter that applies to some requests, in the order of the router.
     */
    private final byte[] decisions;
    /**
     * The sorted filters, when every decision is static and no filter can be disabled.
     */
    private final @Nullable List<GenericHttpFilter> filters;

    RouteFilterPlan(DefaultRouter owner,
                    DefaultUrlRouteInfo<?, ?> routeInfo,
                    List<FilterRoute> alwaysMatches,
                    List<GenericHttpFilter> alwaysMatchesFilters,
                    List<FilterRoute> preconditionFilterRoutes) {
        this.owner = owner;
        this.method = routeInfo.getHttpMethod();
        String template = routeInfo.getUriMatchTemplate().getTemplateString();
        int variableStart = template.indexOf('{');
        this.literalTemplate = variableStart == -1 ? template : null;
        this.literalPrefix = variableStart == -1 ? template : template.substring(0, variableStart);
        int size = preconditionFilterRoutes.size();
        this.decisions = new byte[size];
        boolean dynamic = false;
        for (int i = 0; i < size; i++) {
            FilterRoute filterRoute = preconditionFilterRoutes.get(i);
            byte decision = decide(filterRoute, routeInfo);
            if (decision == MATCH && filterRoute.getFilter() instanceof Toggleable) {
                // may be disabled, so it is checked for each request
                decision = CHECK;
            }
            decisions[i] = decision;
            dynamic |= decision == CHECK;
        }
        if (dynamic) {
            this.filters = null;
        } else {
            List<GenericHttpFilter> httpFilters = new ArrayList<>(alwaysMatches.size() + size);
            // the same list, in the same order, as the router builds for each request, so the sort gives the same result
            httpFilters.addAll(alwaysMatchesFilters);
            for (int i = 0; i < size; i++) {
                if (decisions[i] == MATCH) {
                    httpFilters.add(preconditionFilterRoutes.get(i).getFilter());
                }
            }
            FilterRunner.sort(httpFilters);
            this.filters = Collections.unmodifiableList(httpFilters);
        }
    }

    /**
     * Whether the decisions of this plan hold for a request: its method is the method of the
     * route and its path starts like the template of the route.
     *
     * @param request The request
     * @return Whether the plan applies to the request
     */
    boolean appliesTo(HttpRequest<?> request) {
        if (request.getMethod() != method) {
            return false;
        }
        String path = request.getPath();
        String template = literalTemplate;
        if (template != null) {
            // the route matches the template, with or without a trailing slash
            int length = template.length();
            return path.equals(template)
                || (path.length() == length + 1 && path.charAt(length) == '/' && path.startsWith(template));
        }
        return path.startsWith(literalPrefix);
    }

    /**
     * The filters for a request this plan {@link #appliesTo(HttpRequest) applies to}.
     *
     * @param request                  The request
     * @param alwaysMatchesFilters     The sorted filters that apply to every request
     * @param preconditionFilterRoutes The filters that apply to some requests
     * @return The sorted filters
     */
    List<GenericHttpFilter> filters(HttpRequest<?> request,
                                    List<GenericHttpFilter> alwaysMatchesFilters,
                                    List<FilterRoute> preconditionFilterRoutes) {
        List<GenericHttpFilter> staticFilters = filters;
        if (staticFilters != null) {
            return staticFilters;
        }
        var httpFilters = new ArrayList<GenericHttpFilter>(alwaysMatchesFilters.size() + preconditionFilterRoutes.size());
        httpFilters.addAll(alwaysMatchesFilters);
        HttpMethod requestMethod = request.getMethod();
        String path = request.getPath();
        for (int i = 0; i < decisions.length; i++) {
            byte decision = decisions[i];
            if (decision == MATCH) {
                httpFilters.add(preconditionFilterRoutes.get(i).getFilter());
            } else if (decision == CHECK) {
                preconditionFilterRoutes.get(i).match(requestMethod, path).ifPresent(httpFilters::add);
            }
        }
        FilterRunner.sort(httpFilters);
        return Collections.unmodifiableList(httpFilters);
    }

    private byte decide(FilterRoute filterRoute, DefaultUrlRouteInfo<?, ?> routeInfo) {
        String matchingAnnotation = filterRoute.findMatchingAnnotation();
        if (matchingAnnotation != null && !routeInfo.getAnnotationMetadata().hasStereotype(matchingAnnotation)) {
            // the same check the router does for a request with a route
            return NO_MATCH;
        }
        if (!(filterRoute instanceof DefaultFilterRoute)) {
            // how another kind of filter route matches is not known
            return CHECK;
        }
        // DefaultFilterRoute#match: the methods, then any pattern
        Set<HttpMethod> methods = filterRoute.getFilterMethods();
        if (!methods.isEmpty() && !methods.contains(method)) {
            return NO_MATCH;
        }
        String[] patterns = filterRoute.getPatterns();
        FilterPatternStyle patternStyle = filterRoute.getPatternStyle();
        PathMatcher matcher = patternStyle.getPathMatcher();
        String template = literalTemplate;
        if (template != null) {
            boolean exact = matchesAny(matcher, patterns, template);
            boolean trailingSlash = matchesAny(matcher, patterns, template + '/');
            if (exact != trailingSlash) {
                return CHECK;
            }
            return exact ? MATCH : NO_MATCH;
        }
        if (patternStyle != FilterPatternStyle.ANT) {
            return CHECK;
        }
        byte decision = NO_MATCH;
        for (String pattern : patterns) {
            byte patternDecision = decideAntPattern(pattern, literalPrefix);
            if (patternDecision == MATCH) {
                return MATCH;
            }
            if (patternDecision == CHECK) {
                decision = CHECK;
            }
        }
        return decision;
    }

    private static boolean matchesAny(PathMatcher matcher, String[] patterns, String path) {
        for (String pattern : patterns) {
            if (matcher.matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decides whether an Ant pattern matches every path, or no path, that starts with the given
     * prefix, following {@link io.micronaut.core.util.AntPathMatcher}: the path and the pattern
     * must both start with a separator or both not, and their tokens are compared in order up to
     * the first {@code **} of the pattern, which matches the rest of the path.
     *
     * @param pattern The pattern
     * @param prefix  The start of every path
     * @return The decision
     */
    static byte decideAntPattern(String pattern, String prefix) {
        if (!prefix.startsWith("/")) {
            return CHECK;
        }
        if (!pattern.startsWith("/")) {
            return NO_MATCH;
        }
        // the tokens of the prefix up to its last separator are the first tokens of every path
        String[] pathTokens = StringUtils.tokenizeToStringArray(prefix.substring(0, prefix.lastIndexOf('/') + 1), "/");
        String[] patternTokens = StringUtils.tokenizeToStringArray(pattern, "/");
        boolean exact = true;
        for (int i = 0; i < patternTokens.length; i++) {
            String patternToken = patternTokens[i];
            if ("**".equals(patternToken)) {
                if (!exact) {
                    return CHECK;
                }
                for (int j = i + 1; j < patternTokens.length; j++) {
                    if (!"**".equals(patternTokens[j])) {
                        return CHECK;
                    }
                }
                // every token before matched, and ** matches the rest
                return MATCH;
            }
            if (i >= pathTokens.length) {
                return CHECK;
            }
            if (patternToken.indexOf('*') != -1 || patternToken.indexOf('?') != -1) {
                // a later literal token can still decide that the pattern never matches
                exact = false;
            } else if (!patternToken.equals(pathTokens[i])) {
                return NO_MATCH;
            }
        }
        if (pathTokens.length > patternTokens.length) {
            // the pattern has no ** and ends before the path
            return NO_MATCH;
        }
        return CHECK;
    }
}
