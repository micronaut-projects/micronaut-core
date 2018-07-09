/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.mongo.reactive;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.context.scope.Refreshable;

import javax.inject.Singleton;

/**
 * Factory for named {@link MongoClient} instances. Creates the injectable {@link io.micronaut.context.annotation.Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class NamedReactiveMongoClientFactory {

    /**
     * Factory name to create a client.
     * @param configuration configuration pulled in
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @EachBean(NamedReactiveMongoConfiguration.class)
    @Refreshable(MongoSettings.PREFIX)
    MongoClient mongoClient(NamedReactiveMongoConfiguration configuration) {
        return MongoClients.create(configuration.buildSettings());
    }
}
