/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.tck.tests.hateoas;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class JsonErrorSerdeTest {
    private static final String JSON_ERROR = """
            {"_links":{"self":[{"href":"/resolve","templated":false}]},"_embedded":{"errors":[{"message":"Internal Server Error: Something bad happened"}]},"message":"Internal Server Error"}""";
    private static final String SPEC_NAME = "JsonErrorSerdeTest";

    /**
     * @throws IOException Exception thrown while getting the server under test.
     */
    @Test
    void canDeserializeAJsonErrorAsAGenericResource() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Collections.emptyMap())) {
            JsonMapper jsonMapper = server.getApplicationContext().getBean(JsonMapper.class);
            //when:
            Resource resource = jsonMapper.readValue(JSON_ERROR, Resource.class);
            //then:
            assertTrue(resource.getEmbedded().getFirst("errors").isPresent());
            assertTrue(resource.getLinks().getFirst("self").isPresent());
            assertEquals("/resolve", resource.getLinks().getFirst("self").get().getHref());
            assertFalse(resource.getLinks().getFirst("self").get().isTemplated());
        }
    }
}
