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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The policy that narrows the matches of a request down to the closest ones, see
 * {@link DefaultRouter#resolveAmbiguity(HttpRequest, List)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteAmbiguity {

    private RouteAmbiguity() {
    }

    /**
     * Narrows the given route matches for a request down to the closest ones.
     *
     * @param request     The request
     * @param uriRoutes   The route matches of the request
     * @param engineOrders Whether a match may have a template of an engine with its own order
     * @param <T>         The target type
     * @param <R>         The result type
     * @return The closest matches
     */
    static <T, R> List<UriRouteMatch<T, R>> resolve(HttpRequest<?> request,
                                                    List<UriRouteMatch<T, R>> uriRoutes,
                                                    boolean engineOrders) {
        // if there are multiple routes, try to resolve the ambiguity

        final Collection<MediaType> acceptedProducedTypes = request.accept();
        if (CollectionUtils.isNotEmpty(acceptedProducedTypes)) {
            // take the highest priority accepted type
            final MediaType mediaType = acceptedProducedTypes.iterator().next();
            var mostSpecific = new ArrayList<UriRouteMatch<T, R>>(uriRoutes.size());
            for (UriRouteMatch<T, R> routeMatch : uriRoutes) {
                if (routeMatch.getRouteInfo().explicitlyProduces(mediaType)) {
                    mostSpecific.add(routeMatch);
                }
            }
            if (!mostSpecific.isEmpty()) {
                uriRoutes = mostSpecific;
            }
        }
        boolean permitsBody = request.getMethod().permitsRequestBody();
        int routeCount = uriRoutes.size();
        if (routeCount > 1 && permitsBody) {
            final MediaType contentType = request.getContentType().orElse(MediaType.ALL_TYPE);
            var explicitlyConsumedRoutes = new ArrayList<UriRouteMatch<T, R>>(routeCount);
            var consumesRoutes = new ArrayList<UriRouteMatch<T, R>>(routeCount);

            for (UriRouteMatch<T, R> match : uriRoutes) {
                if (match.getRouteInfo().explicitlyConsumes(contentType)) {
                    explicitlyConsumedRoutes.add(match);
                }
                if (explicitlyConsumedRoutes.isEmpty()) {
                    consumesRoutes.add(match);
                }
            }

            uriRoutes = explicitlyConsumedRoutes.isEmpty() ? consumesRoutes : explicitlyConsumedRoutes;
        }

        /*
         * Any changes to the logic below may also need changes to {@link io.micronaut.http.uri.UriTemplate#compareTo(UriTemplate)}
         */
        routeCount = uriRoutes.size();
        if (routeCount > 1 && engineOrders) {
            Comparator<ParsedRouteTemplate> engineOrder = sameEngineOrder(uriRoutes);
            if (engineOrder != null) {
                return mostSpecific(uriRoutes, engineOrder);
            }
            // the routes of an engine with its own order are not in the Micronaut order in the table
            uriRoutes = new ArrayList<>(uriRoutes);
            uriRoutes.sort(RouteAmbiguity::compareMicronaut);
        }
        if (routeCount > 1) {
            long variableCount = 0;
            long rawLength = 0;

            var closestMatches = new ArrayList<UriRouteMatch<T, R>>(routeCount);

            for (int i = 0; i < routeCount; i++) {
                UriRouteMatch<T, R> match = uriRoutes.get(i);
                UriRouteInfo<T, R> routeInfo = match.getRouteInfo();
                long variable;
                long raw;
                if (routeInfo instanceof DefaultUrlRouteInfo<?, ?> info && !info.isMicronautTemplate()) {
                    // the facts the engine of the template described for this policy
                    variable = info.getPathVariableCount();
                    raw = info.getRawLength();
                } else {
                    UriMatchTemplate template = routeInfo.getUriMatchTemplate();
                    variable = template.getPathVariableSegmentCount();
                    raw = template.getRawSegmentLength();
                }
                if (i == 0) {
                    variableCount = variable;
                    rawLength = raw;
                }
                if (variable > variableCount || raw < rawLength) {
                    break;
                }
                closestMatches.add(match);
            }
            uriRoutes = closestMatches.size() > 1 ? fewestPatternVariables(closestMatches) : closestMatches;
        }
        return uriRoutes;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareMicronaut(UriRouteMatch<?, ?> a, UriRouteMatch<?, ?> b) {
        return ((UriRouteInfo) a.getRouteInfo()).compareTo((UriRouteInfo) b.getRouteInfo());
    }

    /**
     * @param matches The matches of a path
     * @return The order of the engine of the templates of all the matches, or {@code null} if they
     * are of different engines or their engine has no order of its own
     */
    private static @Nullable Comparator<ParsedRouteTemplate> sameEngineOrder(List<? extends UriRouteMatch<?, ?>> matches) {
        String engineId = null;
        for (UriRouteMatch<?, ?> match : matches) {
            ParsedRouteTemplate template = DefaultRouter.engineTemplate(match.getRouteInfo());
            if (template == null) {
                return null;
            }
            if (engineId == null) {
                engineId = template.engineId();
            } else if (!engineId.equals(template.engineId())) {
                return null;
            }
        }
        return engineId == null ? null : RouteTemplateEngines.defaults().comparator(engineId);
    }

    /**
     * The matches whose templates are the most specific by the order of their engine.
     *
     * @param matches The matches, of routes of one engine
     * @param order   The order of the engine
     * @return The most specific matches, in their order
     */
    private static <T, R> List<UriRouteMatch<T, R>> mostSpecific(List<UriRouteMatch<T, R>> matches, Comparator<ParsedRouteTemplate> order) {
        int size = matches.size();
        ParsedRouteTemplate[] templates = new ParsedRouteTemplate[size];
        ParsedRouteTemplate best = null;
        for (int i = 0; i < size; i++) {
            ParsedRouteTemplate template = Objects.requireNonNull(DefaultRouter.engineTemplate(matches.get(i).getRouteInfo()));
            templates[i] = template;
            if (best == null || order.compare(template, best) < 0) {
                best = template;
            }
        }
        var result = new ArrayList<UriRouteMatch<T, R>>(size);
        for (int i = 0; i < size; i++) {
            if (order.compare(templates[i], Objects.requireNonNull(best)) == 0) {
                result.add(matches.get(i));
            }
        }
        return result;
    }

    /**
     * The third key of specificity, among routes with the same literal length and number of
     * variables: fewer variables constrained by a regular expression is more specific, so
     * {@code /t/{id}} is selected over {@code /t/{id:.+}}. It only breaks ties.
     *
     * @param matches The equally specific matches by the first two keys
     * @return The matches with the fewest variables with a regular expression
     */
    private static <T, R> List<UriRouteMatch<T, R>> fewestPatternVariables(List<UriRouteMatch<T, R>> matches) {
        int size = matches.size();
        int[] counts = new int[size];
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            counts[i] = patternVariableCount(matches.get(i).getRouteInfo());
            min = Math.min(min, counts[i]);
        }
        var result = new ArrayList<UriRouteMatch<T, R>>(size);
        for (int i = 0; i < size; i++) {
            if (counts[i] == min) {
                result.add(matches.get(i));
            }
        }
        return result;
    }

    private static int patternVariableCount(UriRouteInfo<?, ?> route) {
        if (route instanceof IndexedRoute indexed) {
            return indexed.getPatternVariableCount();
        }
        return new UriTemplateMatcher(route.getUriMatchTemplate().toString()).getPatternVariableCount();
    }
}
