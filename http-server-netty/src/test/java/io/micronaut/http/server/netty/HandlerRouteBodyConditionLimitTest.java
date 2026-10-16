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
import io.micronaut.http.HttpResponse;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body condition cannot read more of a body than the server may buffer: a limit above
 * {@code micronaut.server.max-request-buffer-size} fails the startup.
 */
class HandlerRouteBodyConditionLimitTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionLimitTest";

    @Test
    void aLimitAboveTheBufferOfTheServerFailsTheStartup() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            try (ApplicationContext ctx = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME,
                "micronaut.server.port", -1,
                "micronaut.server.max-request-buffer-size", "1KB"))) {
                ctx.getBean(EmbeddedServer.class).start();
            }
        });
        String message = messages(e);
        assertTrue(message.contains("POST /limits/imports"), message);
        assertTrue(message.contains("maxBytes(4096)"), message);
        assertTrue(message.contains("1024 bytes of micronaut.server.max-request-buffer-size"), message);
    }

    @Test
    void aLimitWithinTheBufferOfTheServerStarts() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", "4KB"))) {
            assertTrue(ctx.getBean(EmbeddedServer.class).start().isRunning());
        }
    }

    private static String messages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/limits", limits -> {
                // the default limit is the limit of the server
                limits.where(RequestCondition.bodyText(text -> true));
                limits.POST("/imports")
                    .where(RequestCondition.bodyBytes(bytes -> true).maxBytes(4096))
                    .handle((request, pathVariables) -> HttpResponse.ok());
            });
        }
    }
}
