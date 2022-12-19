/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;
import java.io.IOException;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface VersionTest {

    @Test
    default void testControllerMethodWithVersion2() throws IOException {
        TestScenario.builder()
            .configuration(CollectionUtils.mapOf(
                "micronaut.router.versioning.enabled", StringUtils.TRUE,
                "micronaut.router.versioning.header.enabled", StringUtils.TRUE
            )).specName("VersionSpec")
            .request(HttpRequest.GET("/version/ping").header("X-API-VERSION", "2"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("pong v2")
                .build()))
            .run();
    }

    @Controller("/version")
    @Requires(property = "spec.name", value = "VersionSpec")
    class ConsumesController {

        @Get("/ping")
        String pingV1() {
            return "pong v1";
        }

        @Version("2")
        @Get("/ping")
        String pingV2() {
            return "pong v2";
        }
    }
}
