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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.dev.DevAccess;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.management.DevEndpoint;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.Router;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the development pages of a launch: the {@code /dev} endpoint, wherever its route is, when it is routed, and the
 * Control Panel, under {@code micronaut.control-panel.path}, when it is there. An application's own routes at those
 * paths, without them, pass. A request for them must name an allowed host, see
 * {@link DevAccess#allowsHost(String)}, or is refused with a 403; and must carry the launch's access token, in the cookie
 * the link the launcher logs at startup sets or in the {@value DevAccess#TOKEN_HEADER} header, or is refused with a 401.
 * The link's {@code ?token=} sets the cookie and redirects to the same page without it.
 *
 * <p>Other routes are the application's own and pass. Only the development runtime defines this filter.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@ServerFilter("/**")
@Requires(classes = ServerFilter.class)
@DevelopmentActive
@Requires(beans = DevRuntime.class)
public final class DevAccessFilter implements Ordered {

    static final String HOST_REFUSED = "Development mode: this host is not allowed. Use localhost, or add the host to "
        + io.micronaut.dev.manifest.DevManifest.ALLOWED_HOSTS + ".\n";
    static final String TOKEN_NEEDED = "Development mode: this page needs the access token of this launch. Open the link the "
        + "launcher logged at startup (it ends with ?" + DevAccess.TOKEN_PARAMETER + "=...), or send the token in the "
        + DevAccess.TOKEN_HEADER + " header.\n";

    private final DevAccess access;
    private final Environment environment;
    private final BeanProvider<Router> routers;
    private volatile @Nullable Router router; // NOSONAR java:S3077 the primary router, resolved once
    private volatile @Nullable Guarded guarded; // NOSONAR java:S3077 replaced whole when the route table is

    DevAccessFilter(DevRuntime runtime, Environment environment, BeanProvider<Router> routers) {
        this.access = runtime.access();
        this.environment = environment;
        // resolved at the first request: the router creates the filters it routes through
        this.routers = routers;
    }

    /**
     * Refuses a request for a development page that names a host not allowed or lacks the token; hands the token of
     * the startup link over to a cookie.
     *
     * @param request The request
     * @return The refusal or the redirect, or null to proceed
     */
    @RequestFilter
    public @Nullable HttpResponse<?> guard(HttpRequest<?> request) {
        if (!isGuarded(request, guardedPaths())) {
            return null;
        }
        return admit(request, access);
    }

    /**
     * The paths guarded for the route table requests are routed by now, worked out once per table: a rebuild of the
     * development router makes a new one.
     */
    private List<String> guardedPaths() {
        Router primary = router;
        if (primary == null) {
            primary = routers.find(null).orElse(null);
            if (primary == null) {
                return List.of();
            }
            router = primary;
        }
        Router table = primary instanceof DevRouter devRouter ? devRouter.current() : primary;
        Guarded current = guarded;
        if (current == null || current.table() != table) {
            current = new Guarded(table, guardedPaths(table, environment));
            guarded = current;
        }
        return current.paths();
    }

    /**
     * The paths of the development pages a route table has: the development endpoint's when it is routed, and the
     * Control Panel's when it is there. An application's own routes at {@code /dev} or at the Control Panel's path,
     * without them, are not guarded.
     *
     * @param router The route table
     * @param environment The configuration
     * @return The paths, with the context path too when one is set
     */
    static List<String> guardedPaths(Router router, Environment environment) {
        String contextPath = DevAccessLinks.normalizedPath(environment.getProperty(DevAccessLinks.CONTEXT_PATH, String.class).orElse(""));
        List<String> paths = new ArrayList<>();
        for (String path : DevAccessLinks.devEndpointPaths(router)) {
            paths.add(path);
            if (!contextPath.isEmpty() && !isUnder(path, contextPath)) {
                paths.add(contextPath + path);
            }
        }
        String controlPanel = DevAccessLinks.controlPanelPath(environment);
        List<String> candidates = contextPath.isEmpty() ? List.of(controlPanel) : List.of(controlPanel, contextPath + controlPanel);
        for (String candidate : candidates) {
            if (DevAccessLinks.hasControlPanel(router, candidate)) {
                paths.add(candidate);
            }
        }
        return List.copyOf(paths);
    }

    @Override
    public int getOrder() {
        // ahead of the gate filter, the CORS filter and security: a refused request goes no further
        return ServerFilterPhase.FIRST.order();
    }

    /**
     * The answer to a request for a development page.
     *
     * @param request The request
     * @param access The access of the launch
     * @return The refusal or the redirect, or null to proceed
     */
    static @Nullable HttpResponse<?> admit(HttpRequest<?> request, DevAccess access) {
        if (!allowsHost(request, access)) {
            return refusal(HttpStatus.FORBIDDEN, HOST_REFUSED);
        }
        if (!access.tokenEnabled()) {
            return null;
        }
        String offered = request.getParameters().get(DevAccess.TOKEN_PARAMETER);
        if (offered != null) {
            if (!access.accepts(offered)) {
                return refusal(HttpStatus.UNAUTHORIZED, TOKEN_NEEDED);
            }
            Cookie cookie = Cookie.of(access.cookieName(), access.token())
                .httpOnly(true)
                .sameSite(SameSite.Strict)
                .path("/")
                .secure(request.isSecure());
            return HttpResponse.seeOther(URI.create(withoutToken(request.getUri())))
                .cookie(cookie)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer");
        }
        return hasToken(request, access) ? null : refusal(HttpStatus.UNAUTHORIZED, TOKEN_NEEDED);
    }

    /**
     * Whether a request may see what the development runtime reveals of the machine, the source paths of a failed
     * compilation for instance: it names an allowed host, and carries the token unless it is turned off.
     *
     * @param request The request
     * @param access The access of the launch
     * @return True when it may
     */
    static boolean isAuthorized(HttpRequest<?> request, DevAccess access) {
        return allowsHost(request, access) && (!access.tokenEnabled() || hasToken(request, access));
    }

    /**
     * Whether the host a request names is allowed: its {@code Host} header, and the authority of an absolute request
     * target too, which a server may take over the header.
     *
     * @param request The request
     * @param access The access of the launch
     * @return True when it is
     */
    static boolean allowsHost(HttpRequest<?> request, DevAccess access) {
        String host = request.getHeaders().get(HttpHeaders.HOST);
        String authority = request.getUri().getRawAuthority();
        if (host == null && authority == null) {
            return false;
        }
        return (host == null || access.allowsHost(host)) && (authority == null || access.allowsHost(authority));
    }

    private static boolean hasToken(HttpRequest<?> request, DevAccess access) {
        if (access.accepts(request.getHeaders().get(DevAccess.TOKEN_HEADER))) {
            return true;
        }
        // the Cookie headers as sent, read the same way by every server runtime
        String prefix = access.cookieName() + "=";
        for (String header : request.getHeaders().getAll(HttpHeaders.COOKIE)) {
            for (String pair : header.split(";")) {
                String cookie = pair.trim();
                if (cookie.startsWith(prefix) && access.accepts(unquoted(cookie.substring(prefix.length())))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String unquoted(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    }

    /**
     * Whether a request is for a development page: routed to the development endpoint wherever its path puts it, or
     * under one of the guarded paths, compared both as sent and decoded and normalized, as a router may match either.
     */
    static boolean isGuarded(HttpRequest<?> request, List<String> guarded) {
        if (isDevEndpoint(request)) {
            return true;
        }
        URI uri = request.getUri();
        String raw = collapsed(uri.getRawPath());
        String decoded;
        try {
            decoded = collapsed(uri.normalize().getPath());
        } catch (RuntimeException e) {
            decoded = raw;
        }
        for (String path : guarded) {
            if (isUnder(raw, path) || isUnder(decoded, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a request was routed to the development endpoint.
     *
     * @param request The request
     * @return True when it was
     */
    static boolean isDevEndpoint(HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request)
            .filter(MethodBasedRouteMatch.class::isInstance)
            .map(match -> ((MethodBasedRouteMatch<?, ?>) match).getDeclaringType() == DevEndpoint.class)
            .orElse(false);
    }

    private static String collapsed(@Nullable String path) {
        return DevAccessLinks.collapsed(path);
    }

    /**
     * Whether a path is a guarded path or under it: {@code /dev}, {@code /dev/reload} and {@code /dev;x} are, and
     * {@code /devices} is not.
     */
    static boolean isUnder(String path, String guarded) {
        if (!path.startsWith(guarded)) {
            return false;
        }
        if (path.length() == guarded.length()) {
            return true;
        }
        char next = path.charAt(guarded.length());
        return !(Character.isLetterOrDigit(next) || next == '-' || next == '_');
    }

    /**
     * The request target without the token parameter, for the redirect that leaves it out of the address bar and the
     * history.
     */
    static String withoutToken(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        // a path that starts with two slashes would read as another host
        path = collapsed(path);
        String query = uri.getRawQuery();
        if (query == null) {
            return path;
        }
        StringBuilder kept = new StringBuilder();
        for (String parameter : query.split("&")) {
            if (parameter.isEmpty()) {
                continue;
            }
            int equals = parameter.indexOf('=');
            String name = equals < 0 ? parameter : parameter.substring(0, equals);
            // decoded as the request's parameters are, so that an encoded name such as %74oken goes too
            if (decoded(name).equals(DevAccess.TOKEN_PARAMETER)) {
                continue;
            }
            kept.append(kept.isEmpty() ? "" : "&").append(parameter);
        }
        return kept.isEmpty() ? path : path + "?" + kept;
    }

    private static String decoded(String name) {
        try {
            return URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return name;
        }
    }

    private static HttpResponse<?> refusal(HttpStatus status, String message) {
        return HttpResponse.status(status)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(message);
    }

    /**
     * The guarded paths of a route table.
     *
     * @param table The route table
     * @param paths The paths
     */
    private record Guarded(Router table, List<String> paths) {
    }
}
