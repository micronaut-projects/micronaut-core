package io.micronaut.http.client.docs.annotation.headers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.docs.annotation.Pet;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class HeaderTest {

    @Test
    public void testSenderHeaders() throws Exception {

        Map<String,Object> config =Collections.singletonMap(
                "pet.client.id", "11"
        );

        try(EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, config)) {
            PetClient client = embeddedServer.getApplicationContext().getBean(PetClient.class);

            Pet pet = client.get("Fred").blockingGet();

            Assert.assertNotNull(pet);

            Assert.assertEquals(pet.getAge(), 11);
        }

    }
}
