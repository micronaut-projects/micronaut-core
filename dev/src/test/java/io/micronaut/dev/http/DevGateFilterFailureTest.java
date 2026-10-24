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
package io.micronaut.dev.http;

import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevAccess;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.management.DevEndpoint;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevGateFilterFailureTest {

    private static final DevAccess ACCESS = new DevAccess(List.of(), true);
    private static final CompileFailure FAILURE = new CompileFailure(SourceKind.JAVA,
        List.of(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "incompatible types", null, 0, 0)), Instant.now());

    @Test
    void anApplicationRequestIsAnsweredWithTheFailure() {
        HttpResponse<?> response = DevGateFilter.answer(routedTo(local(HttpRequest.GET("/greet")), Greeter.class), FAILURE, null, ACCESS);
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        // an application route under /dev keeps the gate: the route decides, not the path
        assertNotNull(DevGateFilter.answer(routedTo(local(HttpRequest.POST("/dev/reload", "")), Greeter.class), FAILURE, null, ACCESS));
        // as does a request no route matched
        assertNotNull(DevGateFilter.answer(local(HttpRequest.GET("/missing")), FAILURE, null, ACCESS));
    }

    @Test
    void theDevelopmentEndpointIsReachedDespiteTheFailureWherePathConfigurationPutsIt() {
        // the manual reload is the way out of a failure the watcher did not see corrected
        assertNull(DevGateFilter.answer(routedTo(local(HttpRequest.POST("/dev/reload", "")), DevEndpoint.class), FAILURE, null, ACCESS));
        assertNull(DevGateFilter.answer(routedTo(local(HttpRequest.POST("/management/dev/reload", "")), DevEndpoint.class), FAILURE, null, ACCESS));
        assertNull(DevGateFilter.answer(routedTo(local(HttpRequest.GET("/dev")), DevEndpoint.class), FAILURE, null, ACCESS));
    }

    @Test
    void nothingIsAnsweredWhenTheLastCompilationSucceeded() {
        assertNull(DevGateFilter.answer(routedTo(local(HttpRequest.GET("/greet")), Greeter.class), null, null, ACCESS));
    }

    @Test
    void aFailureIsRefusedToAHostThatIsNotAllowed() {
        // a page of another site whose name was made to resolve to this machine
        HttpResponse<?> response = DevGateFilter.answer(routedTo(HttpRequest.GET("/greet").header(HttpHeaders.HOST, "rebind.attacker.example:8080"), Greeter.class), FAILURE, null, ACCESS);
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
        // a request that names no host at all
        assertEquals(HttpStatus.FORBIDDEN, DevGateFilter.answer(HttpRequest.GET("/greet"), FAILURE, null, ACCESS).getStatus());
    }

    @Test
    void theDiagnosticsAreShownOnlyWithTheAccessToken() {
        CompileFailure located = new CompileFailure(SourceKind.JAVA,
            List.of(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "incompatible types", java.nio.file.Path.of("/home/me/app/src/main/java/app/Greeter.java"), 3, 7)), Instant.now());
        HttpResponse<?> generic = DevGateFilter.answer(local(HttpRequest.GET("/greet")), located, null, ACCESS);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, generic.getStatus());
        assertEquals(false, generic.body().toString().contains("Greeter.java"), generic.body().toString());

        HttpResponse<?> detailed = DevGateFilter.answer(local(HttpRequest.GET("/greet")).header(DevAccess.TOKEN_HEADER, ACCESS.token()), located, null, ACCESS);
        assertEquals(true, detailed.body().toString().contains("Greeter.java"), detailed.body().toString());
        HttpResponse<?> cookie = DevGateFilter.answer(local(HttpRequest.GET("/greet")).cookie(io.micronaut.http.cookie.Cookie.of(ACCESS.cookieName(), ACCESS.token())), located, null, ACCESS);
        assertEquals(true, cookie.body().toString().contains("Greeter.java"), cookie.body().toString());

        // with the token turned off, an allowed host sees them
        HttpResponse<?> open = DevGateFilter.answer(local(HttpRequest.GET("/greet")), located, null, new DevAccess(List.of(), false));
        assertEquals(true, open.body().toString().contains("Greeter.java"), open.body().toString());
    }

    private static io.micronaut.http.MutableHttpRequest<?> local(io.micronaut.http.MutableHttpRequest<?> request) {
        return request.header(HttpHeaders.HOST, "localhost:8080");
    }

    private static HttpRequest<?> routedTo(HttpRequest<?> request, Class<?> declaringType) {
        MethodBasedRouteMatch<?, ?> match = (MethodBasedRouteMatch<?, ?>) Proxy.newProxyInstance(
            DevGateFilterFailureTest.class.getClassLoader(),
            new Class<?>[] {MethodBasedRouteMatch.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDeclaringType" -> declaringType;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "route to " + declaringType.getSimpleName();
                default -> throw new UnsupportedOperationException(method.getName());
            });
        RouteAttributes.setRouteMatch(request, match);
        return request;
    }

    static final class Greeter {
    }
}
