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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A declarative client reads and writes a media type listed in
 * {@code micronaut.codec.json.additional-types}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class JsonCodecAdditionalTypeTest {
    public static final String SPEC_NAME = "JsonCodecAdditionalTypeTest";
    public static final String ACME_JSON = "application/vnd.acme+json";

    @Test
    void declarativeClientReadsAndWritesAdditionalJsonType() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of("micronaut.codec.json.additional-types", List.of(ACME_JSON)))) {
            AcmeClient client = server.getApplicationContext().getBean(AcmeClient.class);

            assertEquals(new Widget("widget", 3), client.get());

            HttpResponse<Widget> response = client.getResponse();
            assertEquals(new Widget("widget", 3), response.body());
            assertEquals(ACME_JSON, response.getContentType().map(MediaType::getName).orElse(null));

            assertEquals(new Widget("posted-echo", 8), client.post(new Widget("posted", 7)));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/json-additional-type")
    static class AcmeController {

        @Get(value = "/widget", produces = ACME_JSON)
        Widget get() {
            return new Widget("widget", 3);
        }

        @Post(value = "/widget", consumes = ACME_JSON, produces = ACME_JSON)
        Widget post(@Body Widget widget) {
            return new Widget(widget.name() + "-echo", widget.count() + 1);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @io.micronaut.http.client.annotation.Client("/json-additional-type")
    interface AcmeClient {

        @Get(value = "/widget", consumes = ACME_JSON)
        Widget get();

        @Get(value = "/widget", consumes = ACME_JSON)
        HttpResponse<Widget> getResponse();

        @Post(value = "/widget", produces = ACME_JSON, consumes = ACME_JSON)
        Widget post(@Body Widget widget);
    }

    @Introspected
    record Widget(String name, int count) {
    }
}
