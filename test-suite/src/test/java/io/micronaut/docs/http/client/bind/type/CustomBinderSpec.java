package io.micronaut.docs.http.client.bind.type;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomBinderSpec {

    @Test
    void testBindingToTheRequest() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        MetadataClient client = server.getApplicationContext().getBean(MetadataClient.class);
        String resp = client.get(new Metadata(3.6, 42L));
        Assertions.assertEquals("3.6", resp);

        server.close();
    }
}
