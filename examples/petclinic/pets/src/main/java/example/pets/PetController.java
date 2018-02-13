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
import example.api.v1.PetOperations;
import io.reactivex.Flowable;
import org.particleframework.context.annotation.Value;
import org.particleframework.http.annotation.Controller;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller("/${pets.api.version}/pets")
@Singleton
public class PetController implements PetOperations {

    private final String databaseName;
    private MongoClient mongoClient;

    public PetController(
            @Value("pets.database.name") String databaseName,
            MongoClient mongoClient) {
        this.databaseName = databaseName;
        this.mongoClient = mongoClient;
    }

    @Override
    public Publisher<Pet> list() {
        return mongoClient
                    .getDatabase(databaseName)
                    .getCollection("pets")
                    .find(Pet.class);
    }
}
