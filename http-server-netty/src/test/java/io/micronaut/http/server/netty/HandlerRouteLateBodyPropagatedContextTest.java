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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An asynchronous handler that reads a body which arrives after the handler returned continues
 * like a controller method whose {@code @Body CompletableFuture} completes once the body arrived:
 * on the event loop that receives the body, without the propagated context of the route, e.g. the
 * MDC context a filter added. The handler itself runs with the context.
 */
class HandlerRouteLateBodyPropagatedContextTest {
    private static final String SPEC_NAME = "HandlerRouteLateBodyPropagatedContextTest";

    @Test
    void continuationOfALateBodyOfAControllerDoesNotSeeTheContext() throws Exception {
        // the reference for the handler routes
        assertEquals("handler=trace=t1,route=none,mdc=t1,request=/late/controller;hello:trace=none,route=none,mdc=none,request=none",
            post("/late/controller", "text/plain", "hello"));
    }

    @Test
    void continuationOfALateTextReadDoesNotSeeTheContext() throws Exception {
        assertEquals("handler=trace=t1,route=r-t1,mdc=t1,request=/late/text;hello:trace=none,route=none,mdc=none,request=none",
            post("/late/text", "text/plain", "hello"));
    }

    @Test
    void continuationOfALateBodyReadDoesNotSeeTheContext() throws Exception {
        assertEquals("handler=trace=t1,route=r-t1,mdc=t1,request=/late/body;hello:trace=none,route=none,mdc=none,request=none",
            post("/late/body", "application/json", "{\"name\":\"hello\"}"));
    }

    @Test
    void continuationOfALateFormReadDoesNotSeeTheContext() throws Exception {
        assertEquals("handler=trace=t1,route=r-t1,mdc=t1,request=/late/form;hello:trace=none,route=none,mdc=none,request=none",
            post("/late/form", "application/x-www-form-urlencoded", "name=hello"));
    }

    /**
     * Send the head of the request, wait until the handler asked for the body, then send the body.
     */
    private static String post(String path, String contentType, String body) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Routes routes = ctx.getBean(Routes.class);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                out.write(("POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "X-Trace: t1\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                assertTrue(routes.reading.await(30, TimeUnit.SECONDS), "the handler reads the body");
                out.write(bytes);
                out.flush();
                String response = new String(readAll(socket.getInputStream()), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200 "), response);
                return response.substring(response.indexOf("\r\n\r\n") + 4);
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    static String describe() {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return "trace=" + context.find(Trace.class).map(Trace::id).orElse("none")
            + ",route=" + context.find(RouteTrace.class).map(RouteTrace::id).orElse("none")
            + ",mdc=" + Objects.requireNonNullElse(MDC.get("trace"), "none")
            + ",request=" + ServerRequestContext.currentRequest().map(HttpRequest::getPath).orElse("none");
    }

    record Trace(String id) implements PropagatedContextElement {
    }

    record RouteTrace(String id) implements PropagatedContextElement {
    }

    @ServerFilter("/late/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = Objects.requireNonNull(request.getHeaders().get("X-Trace"));
            propagatedContext.add(new Trace(trace));
            propagatedContext.add(new MdcPropagationContext(Map.of("trace", trace)));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        final CountDownLatch reading = new CountDownLatch(1);

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.asyncPOST("/late/text", (request, pathVariables, body) -> reading(body.text()))
                .consumesAll()
                .before(Routes::addRouteTrace);
            routes.asyncPOST("/late/body", (request, pathVariables, body) -> reading(body.body(Map.class).thenApply(map -> String.valueOf(map.get("name")))))
                .before(Routes::addRouteTrace);
            routes.asyncPOST("/late/form", (request, pathVariables, body) -> reading(body.form().thenApply(form -> form.find("name", String.class).orElse("none"))))
                .consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .before(Routes::addRouteTrace);
        }

        private CompletionStage<HttpResponse<String>> reading(CompletionStage<String> read) {
            // the body is sent once the handler returned: the read completes later, on the event loop
            String handler = describe();
            reading.countDown();
            return read.thenApply(value -> HttpResponse.ok("handler=" + handler + ";" + value + ":" + describe()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        private static void addRouteTrace(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            propagatedContext.add(new RouteTrace("r-" + request.getHeaders().get("X-Trace")));
        }
    }

    @Controller("/late/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class LateBodyController {
        private final Routes routes;

        LateBodyController(Routes routes) {
            this.routes = routes;
        }

        @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> post(@Body CompletableFuture<String> body) {
            // the body is sent once the method returned: the body completes later, on the event loop
            String handler = describe();
            routes.reading.countDown();
            return body.thenApply(value -> "handler=" + handler + ";" + value + ":" + describe());
        }
    }
}
