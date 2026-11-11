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
package io.micronaut.docs.server.callbacks;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRoutesTest {

    @Test
    void theRouteSeesTheOutcomeAndTheAbandonment() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ReportRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            Reports reports = server.getApplicationContext().getBean(Reports.class);
            reports.generate("1").complete("report 1");
            assertEquals("report 1", client.toBlocking().retrieve(HttpRequest.GET("/reports/1")));
            assertTrue(reports.outcome("1").get(10, TimeUnit.SECONDS).isEmpty());

            CompletableFuture<String> report = reports.generate("2");
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                OutputStream out = socket.getOutputStream();
                out.write("GET /reports/2 HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                long deadline = System.currentTimeMillis() + 10_000;
                while (report.getNumberOfDependents() == 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20);
                }
            }
            // the client went away: the work is cancelled, and the outcome is the closed connection
            assertNotNull(reports.outcome("2").get(10, TimeUnit.SECONDS).orElse(null));
            assertTrue(report.isCancelled());
        }
    }
}
