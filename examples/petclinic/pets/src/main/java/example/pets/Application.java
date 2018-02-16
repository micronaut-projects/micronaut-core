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
import com.mongodb.reactivestreams.client.MongoCollection;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.runtime.ParticleApplication;
import org.particleframework.runtime.server.event.ServerStartupEvent;

import javax.inject.Singleton;
import java.util.Arrays;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent>{

    private final MongoClient mongoClient;
    private final PetsConfiguration configuration;

    public Application(MongoClient mongoClient, PetsConfiguration configuration) {
        this.mongoClient = mongoClient;
        this.configuration = configuration;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        MongoCollection<PetEntity> collection = mongoClient.getDatabase(configuration.getDatabaseName())
                                                           .getCollection(configuration.getCollectionName(), PetEntity.class);

        Single.fromPublisher(collection.insertMany(Arrays.asList(
                new PetEntity("Fred", "Dino"),
                new PetEntity("Babe", "Arthur")
        ))).blockingGet();
    }

    public static void main(String[] args) {
        ParticleApplication.run(Application.class);
    }
}
