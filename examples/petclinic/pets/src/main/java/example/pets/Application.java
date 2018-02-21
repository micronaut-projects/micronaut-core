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
import com.mongodb.reactivestreams.client.Success;
import example.api.v1.PetType;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.runtime.ParticleApplication;
import org.particleframework.runtime.server.event.ServerStartupEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class Application implements ApplicationEventListener<ServerStartupEvent>{

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);
    private final MongoClient mongoClient;
    private final PetsConfiguration configuration;
    private final FriendlyUrl friendlyUrl;

    public Application(MongoClient mongoClient, PetsConfiguration configuration, FriendlyUrl friendlyUrl) {
        this.mongoClient = mongoClient;
        this.configuration = configuration;
        this.friendlyUrl = friendlyUrl;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        MongoCollection<PetEntity> collection = mongoClient.getDatabase(configuration.getDatabaseName())
                                                           .getCollection(configuration.getCollectionName(), PetEntity.class);

        Flowable.fromPublisher(collection.drop())
              .flatMap( success -> collection.insertMany(petList()))
                .subscribe(success -> {}, throwable -> {
                  if(LOG.isErrorEnabled()) {
                      LOG.error("Error saving data: {}", throwable.getMessage());
                  }
              });
    }

    public static void main(String[] args) {
        ParticleApplication.run(Application.class);
    }

    List<PetEntity> petList() {
        return Arrays.asList(
            new PetEntity("Fred", "Harry", friendlyUrl.sanitizeWithDashes("Harry"), "photo-1457914109735-ce8aba3b7a79.jpeg").type(PetType.DOG),
            new PetEntity("Fred", "Ron", friendlyUrl.sanitizeWithDashes("Ron"), "photo-1442605527737-ed62b867591f.jpeg").type(PetType.DOG),
            new PetEntity("Arthur", "Hermione", friendlyUrl.sanitizeWithDashes("Hermione"), "photo-1446231855385-1d4b0f025248.jpeg").type(PetType.DOG),
            new PetEntity("Fred", "Malfoy", friendlyUrl.sanitizeWithDashes("Malfoy"), "photo-1489911646836-b08d6ca60ffe.jpeg").type(PetType.CAT),
            new PetEntity("Arthur", "Crabbe", friendlyUrl.sanitizeWithDashes("Crabbe"), "photo-1512616643169-0520ad604fc2.jpeg").type(PetType.CAT),
            new PetEntity("Arthur", "Goyle", friendlyUrl.sanitizeWithDashes("Goyle"), "photo-1505481354248-2ba5d3b9338e.jpeg").type(PetType.CAT)
        );
    }
}
