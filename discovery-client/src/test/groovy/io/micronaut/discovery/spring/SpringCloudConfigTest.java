/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.spring;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class SpringCloudConfigTest {

    private static EmbeddedServer server;
    private static HttpClient client;
    private static EmbeddedServer springCloudServer;

    @BeforeClass
    public static void setupServer() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true");
        Map<String, Object> map = new HashMap<>();
        map.put(MockSpringCloudConfigServer.ENABLED, true);
        map.put("micronaut.server.port", -1);
        map.put("spring.cloud.config.enabled", false);
        map.put("micronaut.environments", "dev,test");
        springCloudServer = ApplicationContext.run(EmbeddedServer.class, map);

        server = ApplicationContext.run(EmbeddedServer.class, CollectionUtils.mapOf(
                "micronaut.environments","test",
                "micronaut.application.name", "spring-config-sample",
                "micronaut.config-client.enabled", true,
                "spring.cloud.config.enabled", true,
                "spring.cloud.config.uri", springCloudServer.getURI()
        ));

        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, LoadBalancer.fixed(server.getURL()));
    }

    @AfterClass
    public static void stopServer() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "");
        if (springCloudServer != null) {
            springCloudServer.stop();
        }
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    public void testReadValueFromSpringCloudConfig() throws Exception {
        HttpRequest request = HttpRequest.GET("/spring-cloud/issues/1");
        String body = client.toBlocking().retrieve(request);
        assertNotNull(body);
        assertThat(body, equalTo("test: issue # 1!"));
    }
}