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
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.management.DevEndpoint;
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

    private static final CompileFailure FAILURE = new CompileFailure(SourceKind.JAVA,
        List.of(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "incompatible types", null, 0, 0)), Instant.now());

    @Test
    void anApplicationRequestIsAnsweredWithTheFailure() {
        HttpResponse<?> response = DevGateFilter.answer(routedTo(HttpRequest.GET("/greet"), Greeter.class), FAILURE, null);
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        // an application route under /dev keeps the gate: the route decides, not the path
        assertNotNull(DevGateFilter.answer(routedTo(HttpRequest.POST("/dev/reload", ""), Greeter.class), FAILURE, null));
        // as does a request no route matched
        assertNotNull(DevGateFilter.answer(HttpRequest.GET("/missing"), FAILURE, null));
    }

    @Test
    void theDevelopmentEndpointIsReachedDespiteTheFailureWherePathConfigurationPutsIt() {
        // the manual reload is the way out of a failure the watcher did not see corrected
        assertNull(DevGateFilter.answer(routedTo(HttpRequest.POST("/dev/reload", ""), DevEndpoint.class), FAILURE, null));
        assertNull(DevGateFilter.answer(routedTo(HttpRequest.POST("/management/dev/reload", ""), DevEndpoint.class), FAILURE, null));
        assertNull(DevGateFilter.answer(routedTo(HttpRequest.GET("/dev"), DevEndpoint.class), FAILURE, null));
    }

    @Test
    void nothingIsAnsweredWhenTheLastCompilationSucceeded() {
        assertNull(DevGateFilter.answer(routedTo(HttpRequest.GET("/greet"), Greeter.class), null, null));
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
