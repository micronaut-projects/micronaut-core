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
