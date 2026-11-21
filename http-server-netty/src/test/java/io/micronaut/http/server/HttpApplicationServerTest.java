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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Choosing the server of an {@link HttpApplication} by its type.
 */
class HttpApplicationServerTest {

    private static final String SPEC_NAME = "HttpApplicationServerTest";

    @Test
    void chooseNettyAndCustomizeItsConfiguration() throws Exception {
        try (ApplicationContext context = HttpApplication.build()
            .port(-1)
            .property("spec.name", SPEC_NAME)
            .server(NettyEmbeddedServer.class)
            .configure(NettyHttpServerConfiguration.class, netty -> netty.setMaxHeaderSize(12345))
            .routes(routes -> routes.GET("/hello", (request, vars) -> HttpResponse.ok("hi").contentType(MediaType.TEXT_PLAIN_TYPE)))
            .start()) {

            NettyEmbeddedServer server = context.getBean(NettyEmbeddedServer.class);
            assertTrue(server.isRunning());
            assertEquals(12345, context.getBean(NettyHttpServerConfiguration.class).getMaxHeaderSize());
            try (HttpClient client = HttpClient.newHttpClient()) {
                assertEquals("hi", client.send(HttpRequest.newBuilder(server.getURI().resolve("/hello")).build(), BodyHandlers.ofString()).body());
            }
        }
    }

    @Test
    void anotherServerIsStartedInsteadOfNetty() {
        try (ApplicationContext context = HttpApplication.run(
            OtherServer.class,
            new String[] {"-spec.name=" + SPEC_NAME},
            routes -> { })) {

            assertTrue(context.getBean(OtherServer.class).isRunning());
            assertTrue(context.getActiveBeanRegistrations(NettyEmbeddedServer.class).isEmpty(), "Netty is not created");
        }
    }

    @Test
    void aServerTypeWithoutABeanFails() {
        HttpApplication application = HttpApplication.build()
            .property("spec.name", SPEC_NAME)
            .server(MissingServer.class);
        ApplicationStartupException e = assertThrows(ApplicationStartupException.class, application::start);
        assertInstanceOf(NoSuchBeanException.class, e.getCause());
    }

    /**
     * A server type that has no bean.
     */
    interface MissingServer extends EmbeddedServer {
    }

    /**
     * A server of another implementation, on the classpath beside Netty.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class OtherServer implements EmbeddedServer {

        private final ApplicationContext context;
        private boolean running;

        OtherServer(ApplicationContext context) {
            this.context = context;
        }

        @Override
        public EmbeddedServer start() {
            running = true;
            return this;
        }

        @Override
        public EmbeddedServer stop() {
            running = false;
            return this;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isKeepAlive() {
            // otherwise Micronaut.start() waits for the server to stop, as for a server without its own threads
            return false;
        }

        @Override
        public int getPort() {
            return 1;
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public URL getURL() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getURI() {
            return URI.create("http://localhost:1");
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return context;
        }

        @Override
        public ApplicationConfiguration getApplicationConfiguration() {
            return context.getBean(ApplicationConfiguration.class);
        }
    }
}
