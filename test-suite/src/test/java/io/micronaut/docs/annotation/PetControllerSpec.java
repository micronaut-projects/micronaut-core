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
