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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevAccess;
import io.micronaut.dev.management.DevEndpoint;
import io.micronaut.http.HttpMethod;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The link the launcher logs at startup to open the development pages with the launch's access token: the Control
 * Panel when the application has one, the {@code /dev} endpoint otherwise; and the address the server is bound to.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevAccessLinks {

    static final String CONTEXT_PATH = "micronaut.server.context-path";
    /**
     * The Control Panel's path setting, read here so that any Control Panel version is guarded.
     */
    static final String CONTROL_PANEL_PATH = "micronaut.control-panel.path";
    static final String DEFAULT_CONTROL_PANEL_PATH = "/control-panel";

    private static final Logger LOG = LoggerFactory.getLogger(DevAccessLinks.class);
    private static final String SERVER_HOST = "micronaut.server.host";

    private DevAccessLinks() {
    }

    /**
     * The link of a generation that runs an HTTP server.
     *
     * @param generation The generation
     * @param access The access of the launch
     * @return The link, or null when the generation runs no server
     */
    public static @Nullable Link of(ApplicationContext generation, DevAccess access) {
        try {
            EmbeddedServer server = generation.getActiveBeanRegistrations(EmbeddedServer.class).stream()
                .map(BeanRegistration::getBean)
                .filter(EmbeddedServer::isRunning)
                .findFirst()
                .orElse(null);
            if (server == null) {
                return null;
            }
            Environment environment = generation.getEnvironment();
            int port = server.getPort();
            String bound = environment.getProperty(SERVER_HOST, String.class).map(String::trim).filter(host -> !host.isEmpty()).orElse(null);
            String named = bound == null || bound.equals("0.0.0.0") || bound.equals("::") || bound.equals("[::]") ? null : bracketed(bound);
            boolean allInterfaces = named == null;
            String host = named == null ? "localhost" : named;
            String contextPath = normalizedPath(environment.getProperty(CONTEXT_PATH, String.class).orElse(""));
            String controlPanel = contextPath + controlPanelPath(environment);
            boolean hasControlPanel = hasRoute(generation, controlPanel);
            String path = controlPanel;
            int linkPort = port;
            String scheme = server.getScheme();
            if (!hasControlPanel) {
                // the development endpoint wherever its route is: endpoints.all.path, endpoints.dev.path and a
                // management port move it
                UriRouteInfo<?, ?> endpoint = devEndpointRoute(generation);
                path = endpoint == null ? contextPath + "/" + DevEndpoint.NAME : endpointPath(endpoint);
                if (endpoint != null && endpoint.getPort() != null && endpoint.getPort() > 0 && endpoint.getPort() != port) {
                    // a management port is a listener of its own, which the server opens without TLS
                    linkPort = endpoint.getPort();
                    scheme = "http";
                }
            }
            String url = scheme + "://" + host + ":" + linkPort + path
                + (access.tokenEnabled() ? "?" + DevAccess.TOKEN_PARAMETER + "=" + access.token() : "");
            String bindAddress = named == null ? "all interfaces (*:" + port + ")" : named + ":" + port;
            return new Link(url, bindAddress, allInterfaces, access.tokenEnabled());
        } catch (RuntimeException | LinkageError e) {
            // a generation that stopped meanwhile, or a server without HTTP: the note goes without the link
            LOG.debug("Cannot build the development link of a generation", e);
            return null;
        }
    }

    /**
     * Whether a generation routes a GET of a path; false for a server that does not route HTTP requests.
     */
    private static boolean hasRoute(ApplicationContext generation, String path) {
        try {
            return generation.findBean(Router.class)
                .map(router -> router.find(HttpMethod.GET, path, null).findAny().isPresent())
                .orElse(false);
        } catch (NoClassDefFoundError e) {
            // a server of another protocol, without the router: it has no development pages of its own
            return false;
        }
    }

    /**
     * The Control Panel's path in a configuration.
     *
     * @param environment The configuration
     * @return The path, starting with a slash, without a trailing one
     */
    static String controlPanelPath(Environment environment) {
        String path = normalizedPath(environment.getProperty(CONTROL_PANEL_PATH, String.class).orElse(DEFAULT_CONTROL_PANEL_PATH));
        return path.isEmpty() ? DEFAULT_CONTROL_PANEL_PATH : path;
    }

    /**
     * A path starting with a slash and without a trailing one; empty for the root.
     *
     * @param path The path
     * @return The path normalized
     */
    static String normalizedPath(String path) {
        String trimmed = collapsed(path.trim());
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /**
     * A path with each run of slashes made one.
     *
     * @param path The path, if any
     * @return The path, empty for none
     */
    static String collapsed(@Nullable String path) {
        if (path == null) {
            return "";
        }
        return path.replaceAll("/{2,}", "/");
    }

    /**
     * The GET route of the development endpoint, its status; none without the management module.
     */
    private static @Nullable UriRouteInfo<?, ?> devEndpointRoute(ApplicationContext generation) {
        try {
            return generation.findBean(Router.class)
                .flatMap(router -> router.uriRoutes()
                    .filter(route -> route.getHttpMethod() == HttpMethod.GET && route.getDeclaringType() == DevEndpoint.class)
                    .findFirst())
                .orElse(null);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    /**
     * The path of a route's template up to its first variable.
     */
    static String endpointPath(UriRouteInfo<?, ?> route) {
        String template = route.getUriMatchTemplate().toString();
        int variable = template.indexOf('{');
        String path = normalizedPath(variable < 0 ? template : template.substring(0, variable));
        return path.isEmpty() ? "/" : path;
    }

    private static String bracketed(String host) {
        return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    /**
     * The link to log.
     *
     * @param url The URL to open, with the token when it is needed
     * @param bindAddress The address the server listens on, for the note
     * @param allInterfaces Whether the server listens on every interface, so other machines reach it
     * @param tokenEnabled Whether the development pages need the token
     */
    public record Link(String url, String bindAddress, boolean allInterfaces, boolean tokenEnabled) {
    }
}
