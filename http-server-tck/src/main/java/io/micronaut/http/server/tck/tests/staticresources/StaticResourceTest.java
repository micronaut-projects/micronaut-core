/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.tck.tests.staticresources;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.server.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StaticResourceTest {
    public static final String SPEC_NAME = "StaticResourceTest";

    @Test
    public void staticResource() throws IOException {
        asserts(SPEC_NAME,
            CollectionUtils.mapOf(
                "micronaut.router.static-resources.assets.mapping", "/assets/**",
                "micronaut.router.static-resources.assets.paths", "classpath:assets"),
            HttpRequest.GET(UriBuilder.of("/assets").path("hello.txt").build()).accept(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpStatus.OK,
                "Hello World",
                Collections.singletonMap(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)));
    }
}
