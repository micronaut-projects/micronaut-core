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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RequestConditions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.micronaut.web.router.builder.RequestPredicates.accept;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An accepted media range of the quality 0 is not acceptable, RFC 9110 12.4.2, with the
 * precedence of RFC 9110 12.5.1.
 */
class AcceptQualityZeroRoutingTest {

    private static final MediaType FLOWED = MediaType.of("text/plain;format=flowed");
    private static final RouteTemplate ITEM = SelectingColonRouteTemplateEngine.template("/flowed/:id");

    private final Router router = RouteTemplateEngineOrderTest.router(null, routes ->
        routes.route(HttpMethod.GET, ITEM).selectedBy(SelectingColonRouteTemplateEngine.INSTANCE).produces(FLOWED).handle((request, variables) -> HttpResponse.ok()));

    @Test
    void parametersOfAWildcardRangeMakeItMoreSpecificWithinItsLevel() {
        MediaType produced = MediaType.of("text/plain;charset=utf-8");
        assertTrue(AcceptedMediaTypes.isAcceptable(produced, accepted("text/*;charset=utf-8", "text/*;q=0", "*/*")));
        assertFalse(AcceptedMediaTypes.isAcceptable(produced, accepted("text/*;charset=utf-8;q=0", "text/*", "*/*")));
        // a concrete type stays more specific than a wildcard range with parameters
        assertTrue(AcceptedMediaTypes.isAcceptable(produced, accepted("text/plain", "text/*;charset=utf-8;q=0")));
    }

    @Test
    void parameterValuesAreCaseSensitiveExceptTheCharset() {
        MediaType profiled = MediaType.of("application/ld+json;profile=\"https://example.org/A\"");
        assertTrue(AcceptedMediaTypes.isAcceptable(profiled, accepted("application/ld+json;profile=\"https://example.org/a\";q=0", "*/*")));
        assertFalse(AcceptedMediaTypes.isAcceptable(profiled, accepted("application/ld+json;profile=\"https://example.org/A\";q=0", "*/*")));
        assertFalse(AcceptedMediaTypes.isAcceptable(MediaType.of("text/plain;charset=UTF-8"), accepted("text/plain;charset=utf-8;q=0", "*/*")));
    }

    @Test
    void parameterizedVariantsKeepThePreferenceOfTheClient() {
        List<MediaType> produces = List.of(FLOWED, MediaType.of("text/plain;format=fixed"));
        MediaType negotiated = AcceptedMediaTypes.negotiate(produces, false,
            accepted("text/plain;format=fixed", "text/plain;format=flowed;q=0.5", "application/json;q=0"));
        assertNotNull(negotiated);
        assertEquals("fixed", negotiated.getParametersMap().get("format"));
    }

    @Test
    void aSpecificRejectionIsNotBypassedByAParameterlessAcceptedType() {
        String accept = "text/plain, text/plain;format=flowed;q=0";
        assertFalse(RequestConditions.matches(accept(FLOWED), get("/x", accept), RouteConditionContext.fallback()));
        assertTrue(RequestConditions.matches(accept(FLOWED), get("/x", "text/plain, text/plain;format=fixed;q=0"), RouteConditionContext.fallback()));
        // a produced range still covers a concrete acceptable accepted type
        assertTrue(RequestConditions.matches(accept(MediaType.of("text/*")), get("/x", "text/plain, text/html;q=0"), RouteConditionContext.fallback()));

        assertNull(router.findClosest(get("/flowed/1", accept)));
        assertNotNull(router.findClosest(get("/flowed/1", "text/plain, text/plain;format=fixed;q=0")));
    }

    @Test
    void anUnrestrictedAcceptConditionIsNotAJsonRequirement() {
        assertTrue(RequestConditions.matches(accept(MediaType.ALL_TYPE), get("/x", "application/json;q=0, */*"), RouteConditionContext.fallback()));
        assertTrue(RequestConditions.matches(accept(MediaType.of("text/*")), get("/x", "application/json;q=0, */*"), RouteConditionContext.fallback()));
        assertFalse(RequestConditions.matches(accept(MediaType.ALL_TYPE), get("/x", "*/*;q=0"), RouteConditionContext.fallback()));
        assertFalse(RequestConditions.matches(accept(MediaType.of("text/*")), get("/x", "text/*;q=0, application/json"), RouteConditionContext.fallback()));
    }

    @Test
    void theQualityParameterNameIsCaseInsensitive() {
        assertTrue(AcceptedMediaTypes.isRejected(MediaType.of("text/plain;Q=0")));
        assertTrue(AcceptedMediaTypes.isRejected(MediaType.of("text/plain;Q=0.000")));
        assertFalse(AcceptedMediaTypes.isRejected(MediaType.of("text/plain;Q=0.5")));
    }

    private static List<MediaType> accepted(String... types) {
        return MediaType.orderedOf(types);
    }

    private static MutableHttpRequest<Object> get(String path, String accept) {
        return HttpRequest.GET(path).header(HttpHeaders.ACCEPT, accept);
    }
}
