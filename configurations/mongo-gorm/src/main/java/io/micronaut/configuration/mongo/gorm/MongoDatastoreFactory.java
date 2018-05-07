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

package io.micronaut.configuration.mongo.gorm;

import com.mongodb.MongoClient;
import grails.gorm.annotation.Entity;
import grails.mongodb.MongoEntity;
import io.micronaut.configuration.gorm.configuration.GormPropertyResolverAdapter;
import io.micronaut.configuration.gorm.event.ConfigurableEventPublisherAdapter;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.env.Environment;
import io.micronaut.spring.core.env.PropertyResolverAdapter;
import org.grails.datastore.mapping.mongo.MongoDatastore;
import org.springframework.transaction.PlatformTransactionManager;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Sets up GORM for MongoDB.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class MongoDatastoreFactory {

    /**
     * Factory method that will return the datastore.
     * @param applicationContext applicationContext
     * @param mongoClient mongoClient
     * @return mongoDatastore
     */
    @Context
    @Bean
    @Primary
    MongoDatastore mongoDatastore(ApplicationContext applicationContext, MongoClient mongoClient) {
        Environment environment = applicationContext.getEnvironment();
        Class[] entities = environment.scan(Entity.class)
            .filter(MongoEntity.class::isAssignableFrom)
            .toArray(Class[]::new);

        PropertyResolverAdapter propertyResolver = new GormPropertyResolverAdapter(
                applicationContext,
                applicationContext
        );
        MongoDatastore datastore = new MongoDatastore(mongoClient, propertyResolver,
            new ConfigurableEventPublisherAdapter(applicationContext),
            entities);
        Iterable services = datastore.getServices();
        for (Object service : services) {
            applicationContext.registerSingleton(
                    service, false
            );
        }
        for (Object service : services) {
            applicationContext.inject(service);
        }
        return datastore;
    }

    /**
     * Return the transaction manager for the database.
     * @param datastore datastore
     * @return transactionManager
     */
    @Singleton
    @Bean
    @Named("mongo")
    @Secondary
    PlatformTransactionManager transactionManager(MongoDatastore datastore) {
        return datastore.getTransactionManager();
    }
}
