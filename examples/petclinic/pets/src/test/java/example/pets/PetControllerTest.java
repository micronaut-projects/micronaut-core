/*
 * Copyright 2018 original authors
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
package example.pets;

import example.api.v1.Pet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.runtime.server.EmbeddedServer;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
/**
 * @author graemerocher
 * @since 1.0
 */
public class PetControllerTest {

    static EmbeddedServer embeddedServer;

    @BeforeClass
    public static void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer.class);
    }

    @AfterClass
    public static void cleanup() {
        if (embeddedServer != null) {
            embeddedServer.stop();
        }
    }

    @Test
    public void testListPets() {
        PetControllerTestClient client = embeddedServer.getApplicationContext().getBean(PetControllerTestClient.class);

        List<Pet> pets = client
                            .list()
                            .blockingGet();
        assertEquals(pets.size(), 0);

//        client.save(new Pet("")).blockingGet();

        Pet dino = client.save(new Pet("Dino")).blockingGet();

        assertNotNull(dino);

        pets = client
                .list()
                .blockingGet();
        assertEquals(pets.size(), 1);
        assertEquals(pets.iterator().next().getName(), dino.getName());

    }
}
