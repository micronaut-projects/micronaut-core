package org.particleframework.configuration.mongo.reactive;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.EachBean;
import org.particleframework.context.annotation.Factory;

@Factory
public class NamedMongoClientFactory {

    @Bean(preDestroy = "close")
    @EachBean(NamedMongoClientURI.class)
    MongoClient mongoClient(NamedMongoClientURI clientURI) {
        return MongoClients.create(clientURI.getUri());
    }
}
