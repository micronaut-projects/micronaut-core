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
package io.micronaut.dev.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /dev} reports the generation, the strategy, the retained beans, the watched roots and
 * the last compilation failure; {@code POST /dev/reload} compiles everything and restarts if
 * anything changed, the manual trigger for an IDE the watcher did not hear from.
 *
 * <p>The endpoint is sensitive, as the management endpoints that act on the application are: it reports absolute paths
 * of the machine and its reload compiles and restarts the application, so it answers 401 until security is configured
 * or {@code endpoints.dev.sensitive} is set to false. The reload is accepted only as a JSON request from no page or
 * from a page of the server's own origin, as the host resolver gives it through a proxy too, so that a page of another
 * site cannot trigger it with a form or a simple request.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Endpoint(id = DevEndpoint.NAME)
@Requires(classes = {Endpoint.class, HttpHostResolver.class})
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
public class DevEndpoint {

    /**
     * The endpoint's name.
     */
    public static final String NAME = "dev";

    static final String KEY_GENERATION = "generation";
    static final String KEY_STRATEGY = "strategy";
    static final String KEY_RELOADING = "reloading";
    static final String KEY_RETAINED = "retainedBeans";
    static final String KEY_WATCHED = "watchedRoots";
    static final String KEY_LIVERELOAD = "liveReload";
    static final String KEY_FAILURE = "lastFailure";
    static final String KEY_ACCEPTED = "accepted";
    static final String KEY_ACTION = "action";
    static final String ACTION_RELOAD = "reload";
    static final String KEY_REASON = "reason";
    static final String HEADER_FETCH_SITE = "Sec-Fetch-Site";

    private final DevRuntime runtime;
    private final HttpHostResolver hostResolver;

    /**
     * @param runtime The runtime
     * @param hostResolver Resolves the origin the server is reached at, through a proxy too
     */
    public DevEndpoint(DevRuntime runtime, HttpHostResolver hostResolver) {
        this.runtime = runtime;
        this.hostResolver = hostResolver;
    }

    /**
     * @return The state of development mode
     */
    @Read
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put(KEY_GENERATION, runtime.generation());
        status.put(KEY_STRATEGY, runtime.strategy().name());
        status.put(KEY_RELOADING, runtime.isReloading());
        status.put(KEY_RETAINED, runtime.retainedCount());
        status.put(KEY_WATCHED, runtime.watchedRoots().stream().map(Object::toString).toList());
        status.put(KEY_LIVERELOAD, runtime.liveReload().map(server -> Map.of("port", server.port(), "connections", server.connections())).orElse(null));
        status.put(KEY_FAILURE, runtime.lastFailure().map(DevEndpoint::describe).orElse(null));
        return status;
    }

    /**
     * {@code POST /dev/reload} requests a reload; it runs after this response is sent, since a restart
     * stops the server answering it.
     *
     * @param action The action, {@code reload} being the only one
     * @param request The request, which must be JSON and not from another origin
     * @return An acknowledgement, {@code accepted} false for an unknown action; 403 for a request from another origin
     */
    @Write
    public HttpResponse<Map<String, Object>> act(@Selector String action, HttpRequest<?> request) {
        String refusal = refusal(request, hostResolver.resolve(request));
        if (refusal != null) {
            return HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ACCEPTED, false, KEY_ACTION, action, KEY_REASON, refusal));
        }
        if (!ACTION_RELOAD.equals(action)) {
            return HttpResponse.ok(Map.of(KEY_ACCEPTED, false, KEY_ACTION, action));
        }
        runtime.requestReload();
        return HttpResponse.ok(Map.of(KEY_ACCEPTED, true, KEY_ACTION, action));
    }

    /**
     * Why a request to act is refused, or null to accept it. A browser sends a form or a simple request to any site
     * without asking, so the action needs a JSON content type, which a page of another origin can send only after a
     * preflight the server does not grant, and a request a browser marks as coming from another site, or whose origin
     * is not the server's own, is refused. A client that is no page, such as curl or an IDE, sends neither header.
     *
     * @param request The request
     * @param ownOrigin The origin the server is reached at, as {@link HttpHostResolver} resolves it from the request
     *                  and the forwarding headers of a proxy
     * @return The reason, or null
     */
    static @Nullable String refusal(HttpRequest<?> request, String ownOrigin) {
        boolean json = request.getContentType().map(type -> type.getName().equalsIgnoreCase(MediaType.APPLICATION_JSON)).orElse(false);
        if (!json) {
            return "The action needs a request with the content type " + MediaType.APPLICATION_JSON;
        }
        String site = request.getHeaders().get(HEADER_FETCH_SITE);
        if (site != null && !site.equalsIgnoreCase("same-origin") && !site.equalsIgnoreCase("none")) {
            return "The action is not accepted from another site";
        }
        String origin = request.getHeaders().get(HttpHeaders.ORIGIN);
        if (origin != null && !sameOrigin(origin, ownOrigin)) {
            return "The action is not accepted from another origin";
        }
        return null;
    }

    /**
     * Whether two origins have the same scheme, host and port, a default port written or left out.
     */
    private static boolean sameOrigin(String origin, String ownOrigin) {
        try {
            URI a = new URI(origin.trim());
            URI b = new URI(ownOrigin.trim());
            return a.getScheme() != null && a.getHost() != null && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost()) && port(a) == port(b);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static Map<String, Object> describe(CompileFailure failure) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("kind", failure.kind().name().toLowerCase(java.util.Locale.ROOT));
        description.put("at", failure.at().toString());
        description.put("errors", failure.errors().stream().map(d -> (d.file() != null ? d.file() + ":" + d.line() + ": " : "") + d.message()).toList());
        description.put("warnings", failure.diagnostics().stream().filter(d -> d.severity() == CompileDiagnostic.Severity.WARNING).count());
        return description;
    }
}
