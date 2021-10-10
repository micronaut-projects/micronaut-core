package io.micronaut.docs.annotation.headers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.annotation.Pet;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class HeaderSpec {

    @Test
    public void testSenderHeaders() throws Exception {

        Map<String,Object> config =Collections.singletonMap(
                "pet.client.id", "11"
        );

        try(EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, config)) {
            PetClient client = embeddedServer.getApplicationContext().getBean(PetClient.class);

            Pet pet = client.get("Fred").blockingGet();

            Assert.assertNotNull(pet);

            Assert.assertEquals(11, pet.getAge());
        }

    }
}
