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
package io.micronaut.http.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A gateway server in front of an {@link Upstream} server, both on random ports. The gateway
 * routes of a test are the {@link GatewayRoutes} beans that require its spec name, and the
 * configuration it gives; {@code test.upstream} is the URL of the upstream.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class GatewayTestBase {

    EmbeddedServer upstream;
    EmbeddedServer gateway;
    ScriptedUpstream scripted;

    abstract String specName();

    Map<String, Object> gatewayProperties() {
        return Map.of();
    }

    Map<String, Object> upstreamProperties() {
        return Map.of();
    }

    @BeforeAll
    void startServers() throws IOException {
        upstream = Upstream.start(upstreamProperties());
        scripted = new ScriptedUpstream();
        Map<String, Object> properties = new HashMap<>(gatewayProperties());
        properties.put("test.scripted", scripted.uri().toString());
        gateway = startGateway(specName(), upstream, properties);
    }

    static EmbeddedServer startGateway(String specName, EmbeddedServer upstream, Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>();
        all.put("spec.name", specName);
        all.put("micronaut.server.port", -1);
        all.put("test.upstream", upstream.getURI().toString());
        all.put("micronaut.server.max-request-size", 256 * 1024 * 1024);
        all.put("micronaut.http.client.read-timeout", "20s");
        all.putAll(properties);
        Object environments = all.remove("test.environments");
        if (environments != null) {
            return ApplicationContext.builder(all).environments(environments.toString().split(",")).run(EmbeddedServer.class);
        }
        return ApplicationContext.run(EmbeddedServer.class, all);
    }

    @AfterAll
    void stopServers() {
        if (gateway != null) {
            gateway.getApplicationContext().close();
        }
        if (upstream != null) {
            upstream.getApplicationContext().close();
        }
        if (scripted != null) {
            try {
                scripted.close();
            } catch (IOException e) {
                // closed
            }
        }
    }

    Upstream.Counters counters() {
        return upstream.getApplicationContext().getBean(Upstream.Counters.class);
    }

    RawHttp.Response get(String path, String... headers) throws IOException {
        return send("GET", path, null, headers);
    }

    RawHttp.Response send(String method, String path, String body, String... headers) throws IOException {
        StringBuilder request = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            .append("Host: localhost:").append(gateway.getPort()).append("\r\n")
            .append("Connection: close\r\n");
        for (String header : headers) {
            request.append(header).append("\r\n");
        }
        if (body != null) {
            request.append("Content-Length: ").append(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append("\r\n");
        }
        request.append("\r\n");
        if (body != null) {
            request.append(body);
        }
        return RawHttp.exchange(gateway.getPort(), request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), method.equals("HEAD"));
    }
}
