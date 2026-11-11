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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceUrlPathTest {
    private static final String SPEC_NAME = "ServiceUrlPathTest";
    private static final String VALUE = "Bob Smith + & / ? # % café";

    @ParameterizedTest
    @CsvSource({
        "/ctx, false, false",
        "/ctx/prefix, false, false",
        "/ctx/prefix/, false, false",
        "/ctx/prefix, true, false",
        "/ctx/prefix, true, true"
    })
    void configuredPathSurvivesQueryBinding(String path, boolean separatePath, boolean overrideUrlPath) throws IOException {
        try (var server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            String baseUrl = server.getURL().orElseThrow().toString();
            var properties = new HashMap<String, Object>();
            properties.put("spec.name", SPEC_NAME);
            String urlPath = separatePath ? (overrideUrlPath ? "/ignored" : "") : path;
            properties.put("micronaut.http.services.path-test.url", baseUrl + urlPath);
            if (separatePath) {
                properties.put("micronaut.http.services.path-test.path", path);
            }
            properties.put("path-test.absolute-url", baseUrl);
            try (var context = ApplicationContext.run(properties)) {
                var client = context.getBean(PathClient.class);
                String expectedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                assertEquals(expectedPath + "/hello", client.noQuery());
                assertEquals(expectedPath + "/hello/Bob", client.pathVariable("Bob"));
                assertEquals(expectedPath + "/hello:" + VALUE, client.templateQuery(VALUE));
                assertEquals(expectedPath + "/hello:" + VALUE, client.appendedQuery(VALUE));
                assertEquals("/absolute:" + VALUE, client.absoluteQuery(VALUE));

                var lowLevelClient = context.getBean(HttpClientRegistry.class)
                    .getClient(HttpVersion.HTTP_1_1, "path-test", null).toBlocking();
                assertEquals("/absolute:a+b&c", lowLevelClient.retrieve(
                    HttpRequest.GET(baseUrl + "/absolute?name=a%2Bb%26c"), String.class));
                assertEquals(expectedPath + "/hello:a+b&c", lowLevelClient.retrieve(
                    HttpRequest.GET("/hello?name=a%2Bb%26c"), String.class));

                var fixedClient = context.getBean(HttpClientRegistry.class)
                    .getClient(HttpVersion.HTTP_1_1, baseUrl + path, null).toBlocking();
                assertEquals(expectedPath + "/hello:a+b&c", fixedClient.retrieve(
                    HttpRequest.GET("/hello?name=a%2Bb%26c"), String.class));
            }
        }
    }

    @Client("path-test")
    @Requires(property = "spec.name", value = SPEC_NAME)
    interface PathClient {
        @Get("/hello")
        @NonNull
        String noQuery();

        @Get("/hello/{name}")
        @NonNull
        String pathVariable(@NonNull String name);

        @Get("/hello{?name}")
        @NonNull
        String templateQuery(@NonNull @QueryValue String name);

        @Get("/hello")
        @NonNull
        String appendedQuery(@NonNull @QueryValue String name);

        @Get("${path-test.absolute-url}/absolute{?name}")
        @NonNull
        String absoluteQuery(@NonNull @QueryValue String name);
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathController {
        @Get(uris = {"/ctx/hello/{name}", "/ctx/prefix/hello/{name}"})
        @NonNull
        String pathVariable(@NonNull String name, @NonNull HttpRequest<?> request) {
            return request.getPath();
        }

        @Get(uris = {"/ctx/hello", "/ctx/prefix/hello"})
        @NonNull
        String hello(@NonNull HttpRequest<?> request) {
            String name = request.getParameters().get("name");
            return request.getPath() + (name == null ? "" : ":" + name);
        }

        @Get("/absolute")
        @NonNull
        String absolute(@NonNull @QueryValue String name) {
            return "/absolute:" + name;
        }
    }
}
