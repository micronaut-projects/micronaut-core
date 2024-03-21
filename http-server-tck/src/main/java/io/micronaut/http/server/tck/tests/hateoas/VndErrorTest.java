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
package io.micronaut.http.server.tck.tests.hateoas;

import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.GenericResource;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.hateoas.VndError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class VndErrorTest {
    private static final String SPEC_NAME = "VndErrorTest";

    /**
     * @throws IOException Exception thrown while getting the server under test.
     */
    @Test
    public void responseCanBeBoundToVndError() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.GET("/vndError"),
                (server, request) -> {
                    Executable e = () -> server.exchange(request, VndError.class);
                    HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, e);
                    Optional<VndError> vndErrorOptional = ex.getResponse().getBody(VndError.class);
                    assertTrue(vndErrorOptional.isPresent());
                    VndError vndError = vndErrorOptional.get();
                    assertEquals("Not Found", vndError.getMessage());
                    OptionalMultiValues<Link> links = vndError.getLinks();
                    assertFalse(links.isEmpty());
                    links.getFirst("self").ifPresent(link -> {
                        assertEquals("/vndError", link.getHref());
                        assertFalse(link.isTemplated());
                    });
                    OptionalMultiValues<Resource> resourceOptionalMultiValues = vndError.getEmbedded();
                    assertFalse(resourceOptionalMultiValues.isEmpty());

                    Optional<List<Resource>> errorsOptional = resourceOptionalMultiValues.get("errors");
                    assertTrue(errorsOptional.isPresent());
                    List<Resource> resources = errorsOptional.get();
                    Optional<GenericResource> genericResourceOptional = resources.stream()
                            .filter(resource -> resource instanceof GenericResource)
                            .map(GenericResource.class::cast)
                            .findFirst();
                    assertTrue(genericResourceOptional.isPresent());
                    assertEquals("Page Not Found", genericResourceOptional.get().getAdditionalProperties().get("message"));
                    });
    }
}
