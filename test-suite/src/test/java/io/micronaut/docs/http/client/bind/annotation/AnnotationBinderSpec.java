package io.micronaut.docs.http.client.bind.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnnotationBinderSpec {

    @Test
    void testBindingToTheRequest() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        MetadataClient client = server.getApplicationContext().getBean(MetadataClient.class);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("version", 3.6);
        metadata.put("deploymentId", 42L);
        String resp = client.get(metadata);
        Assertions.assertEquals("3.6", resp);

        server.close();
    }
}
