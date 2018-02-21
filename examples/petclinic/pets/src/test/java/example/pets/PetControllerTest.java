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

import com.mongodb.reactivestreams.client.MongoClient;
import example.api.v1.Pet;
import example.api.v1.PetType;
import io.reactivex.Flowable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.runtime.server.EmbeddedServer;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
/**
 * @author graemerocher
 * @since 1.0
 */
public class PetControllerTest {

    FriendlyUrl friendlyUrl = new FriendlyUrl();

    static EmbeddedServer embeddedServer;

    @BeforeClass
    public static void setup() {
        embeddedServer = ApplicationContext.run(
                EmbeddedServer.class,
                Collections.singletonMap(
                        "consul.client.registration.enabled",false
                )
        );
    }

    @AfterClass
    public static void cleanup() {
        if (embeddedServer != null) {
            embeddedServer.stop();
        }
    }

    @After
    public void cleanupData() {
        ApplicationContext applicationContext = embeddedServer.getApplicationContext();
        MongoClient mongoClient = applicationContext.getBean(MongoClient.class);
        PetsConfiguration config = applicationContext.getBean(PetsConfiguration.class);
        // drop the data
        Flowable.fromPublisher(mongoClient.getDatabase(config.getDatabaseName())
                    .drop()).blockingFirst();
    }

    @Test
    public void testListPets() {
        PetControllerTestClient client = embeddedServer.getApplicationContext().getBean(PetControllerTestClient.class);

        List<PetEntity> pets = client
                            .list()
                            .blockingGet();
        assertEquals(0, pets.size());

        try {
            client.save(new PetEntity("", "", "", "")).blockingGet();
            fail("Should have thrown a constraint violation");
        } catch (HttpClientResponseException e) {
            assertEquals(e.getStatus(), HttpStatus.BAD_REQUEST);
        }

        PetEntity entity = new PetEntity("Fred", "Harry",friendlyUrl.sanitizeWithDashes("Harry"), "photo-1457914109735-ce8aba3b7a79.jpeg")
                .type(PetType.DOG);
        Pet harry = client.save(entity).blockingGet();

        assertNotNull(harry);

        pets = client
                .list()
                .blockingGet();
        assertEquals(pets.size(), 1);
        assertEquals(pets.iterator().next().getName(), harry.getName());

    }

    @Test
    public void testFindByVendor() {
        PetControllerTestClient client = embeddedServer.getApplicationContext().getBean(PetControllerTestClient.class);

        PetEntity entity = new PetEntity("Fred", "Ron", friendlyUrl.sanitizeWithDashes("Ron"), "photo-1442605527737-ed62b867591f.jpeg")
                .type(PetType.DOG);

        Pet ron = client.save(entity).blockingGet();

        assertNotNull(ron);

        assertEquals(4, client.byVendor("Fred").blockingGet().size());
    }
}
