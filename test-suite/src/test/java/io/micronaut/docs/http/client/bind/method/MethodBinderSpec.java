package io.micronaut.docs.http.client.bind.method;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class MethodBinderSpec {

    @Test
    void testBindingToTheRequest() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        NameAuthorizedClient client = server.getApplicationContext().getBean(NameAuthorizedClient.class);

        String resp = client.get();
        Assertions.assertEquals("Hello, Bob", resp);

        server.close();
    }
}
