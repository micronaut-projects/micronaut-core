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
package io.micronaut.docs.client.ssl;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostnameVerifierSpec {

    @Test
    void hostnameVerifier() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "HostnameVerifierSpec",
            "micronaut.server.ssl.enabled", true,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ))) {
            URL url = server.getURL();
            try (HttpClient client = client(url, (host, session) -> host.equals("localhost"))) {
                assertEquals("ok", client.toBlocking().retrieve("/verified"));
            }
            try (HttpClient client = client(url, (host, session) -> false)) {
                assertThrows(Exception.class, () -> client.toBlocking().retrieve("/verified"));
            }
        }
    }

    private static HttpClient client(URL url, HostnameVerifier verifier) {
        // tag::hostnameVerifier[]
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        ClientSslConfiguration ssl = new ClientSslConfiguration();
        ssl.setInsecureTrustAllCertificates(true); // only for the self-signed test certificate
        ssl.setEndpointIdentification(false);
        ssl.setHostnameVerifier(verifier); // <1>
        configuration.setSslConfiguration(ssl);
        HttpClient client = HttpClient.create(url, configuration);
        // end::hostnameVerifier[]
        return client;
    }

    @Requires(property = "spec.name", value = "HostnameVerifierSpec")
    @Controller
    static class VerifiedController {
        @Get("/verified")
        String get() {
            return "ok";
        }
    }
}
