/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.filters.filtermethods.continuations;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.docs.server.intro.HelloControllerSpec;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceFilterContinuationSpec {

    @Test
    void testTraceFilterWithContinuations() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", HelloControllerSpec.class.getSimpleName());
        map.put("spec.filter", "TraceFilterContinuation");
        map.put("spec.lang", "java");

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map, Environment.TEST);
        HttpClient client = server
            .getApplicationContext()
            .createBean(HttpClient.class, server.getURL());

        HttpResponse response = client.toBlocking().exchange(HttpRequest.GET("/hello"));

        assertEquals("true", response.getHeaders().get("X-Trace-Enabled"));

        server.stop();
        client.stop();
    }
}

