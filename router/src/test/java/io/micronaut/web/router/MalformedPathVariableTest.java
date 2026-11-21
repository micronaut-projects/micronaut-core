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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A path variable that is a malformed percent-encoded sequence still matches its route, and
 * decoding it is a bad request, without the raw value in the message.
 */
class MalformedPathVariableTest {

    private final Router router = router();

    @Test
    void aMalformedVariableIsABadRequest() {
        for (String path : List.of("/items/%C0%AE", "/items/%FF", "/items/%E2%82", "/files/a/%C0%AE", "/files/a/b%E2%82")) {
            assertBadRequest(path);
        }
    }

    private void assertBadRequest(String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        HttpStatusException e = assertThrows(HttpStatusException.class, match::getVariableValues);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertFalse(e.getMessage().contains("%"), e.getMessage());
    }

    @Test
    void theRawValueIsKept() {
        for (String raw : List.of("%C0%AE", "%FF")) {
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/items/" + raw));
            assertNotNull(match, raw);
            assertEquals(raw, match.getRawVariableValues().get("id"));
        }
    }

    @Test
    void aWellFormedVariableIsDecoded() {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/files/a/b%E2%82%AC"));
        assertNotNull(match);
        assertEquals(Map.of("path", "a/b€"), match.getVariableValues());
    }

    private static Router router() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok());
        routes.GET("/files/{+path}", (request, pathVariables) -> HttpResponse.ok());
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
