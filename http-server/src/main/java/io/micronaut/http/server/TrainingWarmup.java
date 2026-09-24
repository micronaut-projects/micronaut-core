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
package io.micronaut.http.server;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * Sends the GET requests of {@link TrainingWarmupConfiguration} to the server once it has started,
 * so that a training run exercises the request path before {@link io.micronaut.runtime.Micronaut}
 * stops the application. It runs after the other {@link ServerStartupEvent} listeners, only for the
 * server that is the {@link EmbeddedApplication} of the context, and a status of 400 or more, or an
 * I/O error, fails the startup.
 *
 * <p>The requests use {@link HttpURLConnection}, so no HTTP client module is needed. They go to
 * the configured {@code micronaut.server.host}, or to the loopback address when no host or a
 * wildcard address is configured: the server then binds the wildcard address, while
 * {@link EmbeddedServer#getHost()} falls back to {@code $HOSTNAME}.</p>
 *
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(property = ApplicationConfiguration.TRAINING_ENABLED, value = StringUtils.TRUE)
final class TrainingWarmup implements ApplicationEventListener<ServerStartupEvent>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(TrainingWarmup.class);
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private final BeanContext beanContext;
    private final HttpServerConfiguration serverConfiguration;
    private final TrainingWarmupConfiguration warmupConfiguration;

    TrainingWarmup(BeanContext beanContext,
                   HttpServerConfiguration serverConfiguration,
                   TrainingWarmupConfiguration warmupConfiguration) {
        this.beanContext = beanContext;
        this.serverConfiguration = serverConfiguration;
        this.warmupConfiguration = warmupConfiguration;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        List<String> paths = warmupConfiguration.getPaths();
        int repeat = warmupConfiguration.getRepeat();
        if (paths.isEmpty() || repeat < 1) {
            return;
        }
        EmbeddedServer server = event.getSource();
        if (beanContext.findBean(EmbeddedApplication.class).orElse(null) != server) {
            // Another server, such as one built with a server factory: it serves other paths
            return;
        }
        String scheme = server.getScheme();
        if (!HttpRequest.SCHEME_HTTP.equals(scheme)) {
            throw new ApplicationStartupException("The training warm-up sends plain HTTP requests, but the server uses " + scheme
                + ". Warm up this server with a ServerStartupEvent listener of the application instead of "
                + TrainingWarmupConfiguration.PREFIX + ".paths");
        }
        String origin = scheme + "://" + requestHost(serverConfiguration.getHost().orElse(null)) + ':' + server.getPort();
        for (int i = 0; i < repeat; i++) {
            for (String path : paths) {
                get(origin + (path.startsWith("/") ? path : "/" + path));
            }
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("Training warm-up sent {} GET requests to {}", paths.size() * repeat, origin);
        }
    }

    private static String requestHost(@Nullable String configuredHost) {
        String host = configuredHost;
        if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            host = InetAddress.getLoopbackAddress().getHostAddress();
        }
        return host.indexOf(':') >= 0 && !host.startsWith("[") ? '[' + host + ']' : host;
    }

    private static void get(String url) {
        int status;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            status = connection.getResponseCode();
            try (InputStream body = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (body != null) {
                    body.transferTo(OutputStream.nullOutputStream());
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ApplicationStartupException("Training warm-up request GET " + url + " failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Training warm-up request GET {} returned {}", url, status);
        }
        if (status >= 400) {
            throw new ApplicationStartupException("Training warm-up request GET " + url + " returned status " + status);
        }
    }
}
