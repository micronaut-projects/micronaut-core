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
package io.micronaut.docs.server.limit;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.server.limit.RequestLimiter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestLimitTest {

    @Test
    void bothRoutesGoThroughTheNamedLimiter() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", "RequestLimitTest",
                "micronaut.server.limits.reports.max-concurrent", 2));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            RequestLimiter limiter = server.getApplicationContext().getBean(RequestLimiter.class, Qualifiers.byName("reports"));
            assertEquals("report 1", client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/1")));
            assertEquals("report 2", client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/functional/2")));
            assertEquals(2, limiter.getAcquiredCount());
        }
    }
}
