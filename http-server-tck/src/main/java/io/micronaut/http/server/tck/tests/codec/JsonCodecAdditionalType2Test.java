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
package io.micronaut.http.server.tck.tests.codec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class JsonCodecAdditionalType2Test {
    public static final String SPEC_NAME = "JsonCodecAdditionalType2Test";
    public static final String CUSTOM_MEDIA_TYPE = "application/json+feed";

    @Test
    void itIsPossibleToCanRegisterAdditionTypesForJsonCodec() throws IOException {
        assertRequest("/json-additional-codec");
        assertRequest("/json-additional-codec/pojo");
    }

    private void assertRequest(String uri) throws IOException {
        HttpResponseAssertion assertion = HttpResponseAssertion.builder()
            .body(BodyAssertion.builder().body("https://jsonfeed.org").contains())
            .status(HttpStatus.OK)
            .assertResponse(response -> assertTrue(response.header("Content-Type").contains(CUSTOM_MEDIA_TYPE)))
            .build();
        Map<String, Object> config = Collections.singletonMap("micronaut.codec.json.additional-types", Collections.singletonList(CUSTOM_MEDIA_TYPE));
        asserts(SPEC_NAME,
            config,
            HttpRequest.GET(uri).header(HttpHeaders.ACCEPT, CUSTOM_MEDIA_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, assertion));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class JsonFeedController {

        @Produces(CUSTOM_MEDIA_TYPE)
        @Get("/json-additional-codec")
        String index() {
            return """
                {
                    "version": "https://jsonfeed.org/version/1",
                    "title": "My Example Feed",
                    "home_page_url": "https://example.org/",
                    "feed_url": "https://example.org/feed.json",
                    ]
                }\
                """;
        }

        @Produces(CUSTOM_MEDIA_TYPE)
        @Get("/json-additional-codec/pojo")
        JsonFeed pojo() {
            return new JsonFeed("https://jsonfeed.org/version/1", "My Example Feed", "https://example.org/", "https://example.org/feed.json");
        }
    }

    @Introspected
    static class JsonFeed {
        private final String version;
        private final String title;
        @JsonProperty("home_page_url")
        private final String homePageUrl;
        @JsonProperty("feed_url")
        private final String feedUrl;

        public JsonFeed(String version, String title, String homePageUrl, String feedUrl) {
            this.version = version;
            this.title = title;
            this.homePageUrl = homePageUrl;
            this.feedUrl = feedUrl;
        }

        public String getVersion() {
            return version;
        }

        public String getTitle() {
            return title;
        }

        public String getHomePageUrl() {
            return homePageUrl;
        }

        public String getFeedUrl() {
            return feedUrl;
        }
    }
}
