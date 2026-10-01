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
package io.micronaut.http.server.filter;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaderEntry;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResponseHeaderPopulatorFilterTest {

    @Test
    void responseHeaderPopulatorIsNotLoadedWhenNoPoliciesAreConfigured() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertFalse(context.findBean(ResponseHeaderPopulator.class).isPresent());
        }
    }

    @Test
    void filterPassesResponseToPopulatorAndAddsAllHeaders() {
        HttpRequest<?> request = HttpRequest.GET("/");
        MutableHttpResponse<?> response = HttpResponse.ok();
        ResponseHeaderPopulator populator = (actualRequest, actualResponse) -> {
            assertSame(request, actualRequest);
            assertSame(response, actualResponse);
            assertEquals(200, actualResponse.code());
            return List.of(
                new HttpHeaderEntry("X-First", "first"),
                new HttpHeaderEntry("X-Second", "second")
            );
        };

        new ResponseHeaderPopulatorFilter(List.of(populator)).filterResponse(request, response);

        assertEquals("first", response.getHeaders().get("X-First"));
        assertEquals("second", response.getHeaders().get("X-Second"));
    }
}
