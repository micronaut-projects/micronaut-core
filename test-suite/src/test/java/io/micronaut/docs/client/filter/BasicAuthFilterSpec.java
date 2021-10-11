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
package io.micronaut.docs.client.filter;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class BasicAuthFilterSpec {

    @Test
    public void testTheFilterIsApplied() {
        try (final EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "BasicAuthFilterSpec"))) {

            ApplicationContext applicationContext = server.getApplicationContext();
            BasicAuthClient client = applicationContext.getBean(BasicAuthClient.class);

            assertEquals("user:pass", client.getMessage());
        }
    }

    @Requires(property = "spec.name", value = "BasicAuthFilterSpec")
    @Controller("/message")
    public static class BasicAuthController {

        @Get
        String message(io.micronaut.http.BasicAuth basicAuth) {
            return basicAuth.getUsername() + ":" + basicAuth.getPassword();
        }
    }

}
