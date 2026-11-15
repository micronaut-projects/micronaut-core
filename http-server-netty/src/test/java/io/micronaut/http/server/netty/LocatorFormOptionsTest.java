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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.FormOptions;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The form options and the executor of a route locator.
 */
class LocatorFormOptionsTest {
    private static final String SPEC_NAME = "LocatorFormOptionsTest";

    private static ApplicationContext ctx;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    private static String post(String uri, String form) {
        return client.toBlocking().retrieve(HttpRequest.POST(uri, form).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    private static HttpStatus failure(String uri, String form) {
        BlockingHttpClient blocking = client.toBlocking();
        HttpRequest<?> request = HttpRequest.POST(uri, form).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        try {
            blocking.retrieve(request);
        } catch (HttpClientResponseException e) {
            return e.getStatus();
        }
        throw new AssertionError("expected a failure for " + uri);
    }

    @Test
    void theDefaultOptionsReadTheDecodedFormWithoutARawView() {
        assertEquals("a/b|no raw view", post("/plain/show", "v=a%2Fb"));
    }

    @Test
    void theRawViewOfAFormWithoutFields() {
        assertEquals("0|0", post("/view/show", ""));
    }

    @Test
    void aValueThatIsNotPercentEncodedIsABadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, failure("/view/show", "v=%zz"));
    }

    @Test
    void theRawFormOfABodyAFilterReplacedIsAnError() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure("/replaced/view/show", "v=a"));
    }

    @Test
    void aLocatorOnAnExecutorThatReturnsNoStageIsAnError() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure("/nostage/show", "v=a"));
    }

    @Test
    void theExceptionOfALocatorOnAnExecutorIsAnError() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure("/throwing/show", "v=checked"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure("/throwing/show", "v=completion"));
    }

    @ServerFilter("/replaced/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacingFilter {
        @RequestFilter
        @PreMatching
        void replace(MutableHttpRequest<?> request) {
            request.uri(URI.create(request.getPath().substring("/replaced".length())));
            ((MutableHttpRequest<Object>) request).body(Map.of("v", "replaced"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.locator("/plain")
                .form(FormOptions.defaults())
                .locate((request, pathVariables, form) -> {
                    String raw;
                    try {
                        raw = form.raw().getString("v");
                    } catch (UnsupportedOperationException e) {
                        raw = "no raw view";
                    }
                    return form.getString("v") + "|" + raw;
                }, target -> new ShowRoutes());
            routes.locator("/view")
                .form(FormOptions.defaults().withRawView())
                .locate((request, pathVariables, form) -> form.names().size() + "|" + form.raw().names().size(),
                    target -> new ShowRoutes());
            routes.locator("/nostage")
                .executeOn(TaskExecutors.BLOCKING)
                .form()
                .locateAsync((request, pathVariables, form) -> null, target -> new ShowRoutes());
            routes.locator("/throwing")
                .executeOn(TaskExecutors.BLOCKING)
                .form()
                .locateAsync((request, pathVariables, form) -> {
                    if ("completion".equals(form.getString("v"))) {
                        throw new CompletionException(new IllegalStateException("the locator failed"));
                    }
                    throw new Exception("the locator failed");
                }, target -> new ShowRoutes());
        }
    }

    static final class ShowRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.POST("/show").consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE).handle((request, pathVariables) ->
                HttpResponse.ok((String) LocatedRoutes.locatedTarget(pathVariables)).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
