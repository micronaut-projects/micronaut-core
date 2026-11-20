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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The copies of a URI route match, with a selected media type, other match information or a
 * rejection status, keep the route of the match.
 */
class UriRouteMatchCopiesTest {

    @Test
    void theCopiesKeepTheRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        builder.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok());
        builder.close();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        DefaultUriRouteMatch<Object, Object> match = (DefaultUriRouteMatch<Object, Object>) router.findClosest(HttpRequest.GET("/items/1"));
        UriRouteInfo<Object, Object> route = match.getRouteInfo();

        for (DefaultUriRouteMatch<Object, Object> copy : List.of(
            match.withSelectedMediaType(MediaType.TEXT_PLAIN_TYPE),
            match.withRejectionStatus(HttpStatus.NOT_ACCEPTABLE),
            match.withMatchInfo(match.getRouteInfo().match("/items/2").orElseThrow()))) {
            assertSame(route, copy.getRouteInfo());
            assertEquals(HttpMethod.GET, copy.getHttpMethod());
        }
        assertEquals("GET - /items/1", match.toString());
    }
}
