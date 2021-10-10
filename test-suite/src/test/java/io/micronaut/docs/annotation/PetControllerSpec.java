package io.micronaut.docs.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.validation.ConstraintViolationException;

import static org.junit.Assert.assertEquals;

public class PetControllerSpec {

    // tag::errorRule[]
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    // end::errorRule[]

    @Test
    public void testPostPet() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient.class);

        // tag::post[]
        Pet pet = client.save("Dino", 10).blockingGet();

        assertEquals("Dino", pet.getName());
        assertEquals(10, pet.getAge());
        // end::post[]

        embeddedServer.stop();
    }

    @Test
    public void testPostPetValidation() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class);
        PetClient client = embeddedServer.getApplicationContext().getBean(PetClient.class);

        // tag::error[]
        thrown.expect(ConstraintViolationException.class);
        thrown.expectMessage("save.age: must be greater than or equal to 1");
        client.save("Fred", -1).blockingGet();
        // end::error[]


        embeddedServer.stop();
    }
}
