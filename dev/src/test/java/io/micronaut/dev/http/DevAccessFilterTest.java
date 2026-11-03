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

import io.micronaut.dev.DevAccess;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The development pages answer only allowed hosts, and only with the launch's access token.
 */
class DevAccessFilterTest {

    private static final List<String> GUARDED = List.of("/dev", "/control-panel");

    private final DevAccess access = new DevAccess(List.of("workspace.example.test"), true);

    @Test
    void aHostThatIsNotAllowedIsRefused() {
        // a page of a site whose name was made to resolve to this machine
        HttpResponse<?> rebound = DevAccessFilter.admit(HttpRequest.GET("/control-panel").header(HttpHeaders.HOST, "rebind.attacker.example:8080")
            .header(DevAccess.TOKEN_HEADER, access.token()), access);
        assertNotNull(rebound);
        assertEquals(HttpStatus.FORBIDDEN, rebound.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(HttpRequest.GET("/dev"), access).getStatus());
        // a suffix or a prefix of an allowed name is another name
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(local("/dev", "localhost.attacker.example"), access).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(local("/dev", "evilworkspace.example.test"), access).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(local("/dev", "127.0.0.1.attacker.example"), access).getStatus());
        // an absolute request target names its host too
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(HttpRequest.GET("http://rebind.attacker.example/dev").header(HttpHeaders.HOST, "localhost"), access).getStatus());
    }

    @Test
    void theLoopbackNamesAndTheAllowedHostsAreAnswered() {
        for (String host : List.of("localhost", "localhost:8080", "LOCALHOST:8080", "app.localhost:8080", "127.0.0.1:8080", "[::1]:8080", "workspace.example.test", "workspace.example.test:443")) {
            HttpResponse<?> response = DevAccessFilter.admit(local("/dev", host).header(DevAccess.TOKEN_HEADER, access.token()), access);
            assertNull(response, host);
        }
        assertTrue(access.allowsHost("localhost."));
        assertFalse(access.allowsHost("localhost:80:80"));
        assertFalse(access.allowsHost("[::2]:8080"));
        assertFalse(access.allowsHost(""));
        assertFalse(access.allowsHost(null));
    }

    @Test
    void aRequestWithoutTheTokenIsRefusedWithAHint() {
        HttpResponse<?> response = DevAccessFilter.admit(local("/control-panel", "localhost:8080"), access);
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
        assertTrue(response.body().toString().contains("Open the link the launcher logged at startup"), response.body().toString());
        // a wrong token, in any form
        assertEquals(HttpStatus.UNAUTHORIZED, DevAccessFilter.admit(local("/dev", "localhost").header(DevAccess.TOKEN_HEADER, "guess"), access).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, DevAccessFilter.admit(local("/dev?token=guess", "localhost"), access).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, DevAccessFilter.admit(local("/dev", "localhost").cookie(Cookie.of(access.cookieName(), "guess")), access).getStatus());
        // another launch's token
        DevAccess other = new DevAccess(List.of(), true);
        assertNotEquals(other.token(), access.token());
        assertEquals(HttpStatus.UNAUTHORIZED, DevAccessFilter.admit(local("/dev", "localhost").header(DevAccess.TOKEN_HEADER, other.token()), access).getStatus());
    }

    @Test
    void theStartupLinkSetsTheCookieAndRedirectsWithoutTheToken() {
        HttpResponse<?> response = DevAccessFilter.admit(local("/control-panel/beans?filter=x&token=" + access.token() + "&page=2", "localhost:8080"), access);
        assertNotNull(response);
        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals("/control-panel/beans?filter=x&page=2", response.getHeaders().get(HttpHeaders.LOCATION));
        String cookie = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith(access.cookieName() + "=" + access.token()), cookie);
        assertTrue(cookie.toLowerCase(java.util.Locale.ROOT).contains("httponly"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
        assertTrue(cookie.contains("Path=/"), cookie);
        assertEquals("no-store", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));

        // the cookie, or the header for curl and an IDE, opens the pages from then on
        assertNull(DevAccessFilter.admit(local("/control-panel/beans", "localhost:8080").cookie(Cookie.of(access.cookieName(), access.token())), access));
        assertNull(DevAccessFilter.admit(local("/dev", "localhost:8080").header(DevAccess.TOKEN_HEADER, access.token()), access));
    }

    @Test
    void theRedirectKeepsThePathAndTheOtherParameters() {
        assertEquals("/control-panel", DevAccessFilter.withoutToken(URI.create("/control-panel?token=abc")));
        assertEquals("/dev?a=1", DevAccessFilter.withoutToken(URI.create("/dev?token=abc&a=1")));
        assertEquals("/dev?tokens=1", DevAccessFilter.withoutToken(URI.create("/dev?tokens=1&token")));
        // never a path that a browser would read as another host
        assertEquals("/x/y", DevAccessFilter.withoutToken(URI.create("/x//y?token=abc")));
        // a name encoded is the same parameter as the request reads it
        assertEquals("/dev?a=1", DevAccessFilter.withoutToken(URI.create("/dev?%74oken=abc&a=1")));
        assertEquals("/dev", DevAccessFilter.withoutToken(URI.create("/dev?t%6Fken=abc&token=def")));
        // a request target that reads as another host is refused before any redirect
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(local("//evil.example/dev?token=" + access.token(), "localhost"), access).getStatus());
    }

    @Test
    void theTokenCanBeTurnedOff() {
        DevAccess open = new DevAccess(List.of(), false);
        assertNull(DevAccessFilter.admit(local("/control-panel", "localhost:8080"), open));
        // the hosts are still checked
        assertEquals(HttpStatus.FORBIDDEN, DevAccessFilter.admit(local("/control-panel", "rebind.attacker.example"), open).getStatus());
    }

    @Test
    void theDevelopmentPagesAreGuardedAndTheApplicationsRoutesAreNot() {
        for (String path : List.of("/dev", "/dev/", "/dev/reload", "/dev;x", "/dev//reload", "/control-panel", "/control-panel/", "/control-panel/api/beans",
            "/%64ev", "/x/../dev", "/control-panel/../control-panel/x")) {
            assertTrue(DevAccessFilter.isGuarded(HttpRequest.GET(path), GUARDED), path);
        }
        for (String path : List.of("/", "/devices", "/dev-tools", "/control-panels", "/hello", "/api/dev")) {
            assertFalse(DevAccessFilter.isGuarded(HttpRequest.GET(path), GUARDED), path);
        }
    }

    @Test
    void onlyTheDevelopmentPagesARouteTableHasAreGuarded() {
        Class<?> panel = io.micronaut.controlpanel.ui.TestPanel.class;
        try (io.micronaut.context.ApplicationContext context = io.micronaut.context.ApplicationContext.run()) {
            io.micronaut.context.env.Environment environment = context.getEnvironment();
            // the application's own routes at /dev and /control-panel, without the endpoint or the Control Panel
            assertEquals(List.of(), DevAccessFilter.guardedPaths(router(route("/dev"), route("/control-panel/api/beans")), environment));
            // the endpoint, wherever its route is, and the Control Panel, told by its package
            assertEquals(List.of("/management/dev", "/control-panel"), DevAccessFilter.guardedPaths(
                router(route("/management/dev", io.micronaut.dev.management.DevEndpoint.class), route("/management/dev/{action}", io.micronaut.dev.management.DevEndpoint.class),
                    route("/control-panel/api/beans", panel)), environment));
        }
        // at the configured path only, and with the context path
        try (io.micronaut.context.ApplicationContext context = io.micronaut.context.ApplicationContext.run(
            java.util.Map.of("micronaut.control-panel.path", "/admin", "micronaut.server.context-path", "/app"))) {
            io.micronaut.context.env.Environment configured = context.getEnvironment();
            assertEquals(List.of(), DevAccessFilter.guardedPaths(router(route("/app/control-panel", panel)), configured));
            assertEquals(List.of("/app/admin"), DevAccessFilter.guardedPaths(router(route("/app/admin", panel)), configured));
        }
    }

    @Test
    void theControlPanelPathIsNormalized() {
        assertEquals("/admin", DevAccessLinks.normalizedPath("admin/"));
        assertEquals("/admin/panel", DevAccessLinks.normalizedPath("//admin//panel//"));
        assertEquals("", DevAccessLinks.normalizedPath("/"));
    }

    @Test
    void theStartupLinkFollowsTheEndpointWhereverItsRouteIs() {
        assertEquals("/management/dev", DevAccessLinks.endpointPath(route("/management/dev")));
        assertEquals("/dev", DevAccessLinks.endpointPath(route("/dev/{action}")));
    }

    private static io.micronaut.web.router.UriRouteInfo<?, ?> route(String template) {
        return route(template, Object.class);
    }

    private static io.micronaut.web.router.UriRouteInfo<?, ?> route(String template, Class<?> declaringType) {
        io.micronaut.http.uri.UriMatchTemplate uriTemplate = io.micronaut.http.uri.UriMatchTemplate.of(template);
        return (io.micronaut.web.router.UriRouteInfo<?, ?>) java.lang.reflect.Proxy.newProxyInstance(DevAccessFilterTest.class.getClassLoader(),
            new Class<?>[] {io.micronaut.web.router.UriRouteInfo.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUriMatchTemplate" -> uriTemplate;
                case "getDeclaringType" -> declaringType;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> template;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static io.micronaut.web.router.Router router(io.micronaut.web.router.UriRouteInfo<?, ?>... routes) {
        return (io.micronaut.web.router.Router) java.lang.reflect.Proxy.newProxyInstance(DevAccessFilterTest.class.getClassLoader(),
            new Class<?>[] {io.micronaut.web.router.Router.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "uriRoutes" -> java.util.Arrays.stream(routes);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static MutableHttpRequest<?> local(String uri, String host) {
        return HttpRequest.GET(uri).header(HttpHeaders.HOST, host);
    }
}
