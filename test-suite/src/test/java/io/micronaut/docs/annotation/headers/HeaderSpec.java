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
