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
import org.junit.*;
import io.micronaut.configuration.mongo.reactive.MongoSettings;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.runners.MethodSorters;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
/**
 * @author graemerocher
 * @since 1.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PetControllerTest {


    static EmbeddedServer embeddedServer;

    @BeforeClass
    public static void setup() {
        embeddedServer = ApplicationContext.run(
                EmbeddedServer.class,
                CollectionUtils.mapOf(
                        MongoSettings.MONGODB_URI, "mongodb://localhost:" + SocketUtils.findAvailableTcpPort(),
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
            client.save(new PetEntity("", "", "")).blockingGet();
            fail("Should have thrown a constraint violation");
        } catch (HttpClientResponseException e) {
            assertEquals(e.getStatus(), HttpStatus.BAD_REQUEST);
        }

        PetEntity entity = new PetEntity("Fred", "Harry","photo-1457914109735-ce8aba3b7a79.jpeg")
                .type(PetType.CAT);
        Pet harry = client.save(entity).blockingGet();

        assertNotNull(harry);

        assertEquals(harry.getImage(), entity.getImage());
        assertEquals(harry.getName(), entity.getName());
        assertEquals(harry.getType(), entity.getType());

        pets = client
                .list()
                .blockingGet();
        assertEquals(pets.size(), 1);
        assertEquals(pets.iterator().next().getName(), harry.getName());

    }

    @Test
    public void testNextFindByVendor() {
        PetControllerTestClient client = embeddedServer.getApplicationContext().getBean(PetControllerTestClient.class);

        PetEntity entity = new PetEntity("Fred", "Ron", "photo-1442605527737-ed62b867591f.jpeg")
                .type(PetType.DOG);

        Pet ron = client.save(entity).blockingGet();

        assertNotNull(ron);

        assertEquals(1, client.byVendor("Fred").blockingGet().size());
    }
}
