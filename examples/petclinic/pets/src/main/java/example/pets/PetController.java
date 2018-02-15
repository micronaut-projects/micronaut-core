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

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import example.api.v1.Pet;
import example.api.v1.PetOperations;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.particleframework.context.annotation.Value;
import org.particleframework.http.annotation.Controller;
import org.particleframework.validation.Validated;

import javax.inject.Singleton;
import javax.validation.Valid;
import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller("/${pets.api.version}/pets")
@Singleton
@Validated
public class PetController implements PetOperations<PetEntity> {

    private final PetsConfiguration configuration;
    private MongoClient mongoClient;

    public PetController(
            PetsConfiguration configuration,
            MongoClient mongoClient) {
        this.configuration = configuration;
        this.mongoClient = mongoClient;
    }

    @Override
    public Single<List<PetEntity>> list() {
        return Flowable.fromPublisher(
                getCollection()
                    .find()
        ).toList();
    }

    @Override
    public Single<List<PetEntity>> byVendor(String name) {
        return Flowable.fromPublisher(
                getCollection()
                    .find(Filters.eq("vendor", name))
        ).toList();
    }

    @Override
    public Single<PetEntity> save(@Valid PetEntity pet) {
        return Single.fromPublisher(getCollection().insertOne(pet))
                     .map(success -> pet);
    }

    private MongoCollection<PetEntity> getCollection() {
        return mongoClient
                .getDatabase(configuration.getDatabaseName())
                .getCollection(configuration.getCollectionName(), PetEntity.class);
    }
}
