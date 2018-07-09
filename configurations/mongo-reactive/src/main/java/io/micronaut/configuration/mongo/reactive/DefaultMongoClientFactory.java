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

import com.mongodb.MongoClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * Builds the primary MongoClient.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = MongoClient.class)
@Requires(beans = DefaultMongoConfiguration.class)
@Factory
public class DefaultMongoClientFactory {

    /**
     * Factory method to return a client.
     * @param configuration configuration pulled in
     * @return mongoClient
     */
    @Bean(preDestroy = "close")
    @Primary
    @Singleton
    MongoClient mongoClient(DefaultMongoConfiguration configuration) {
        return new MongoClient(configuration.buildURI());
    }
}
