package org.particleframework.configuration.mongo.reactive;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.particleframework.context.annotation.*;

import javax.inject.Singleton;

/**
 * Factory for the default {@link MongoClient}. Creates the injectable {@link Primary} bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = "particle.mongo.uri")
@Requires(missingProperty = "particle.mongo.uris")
@Factory
public class DefaultMongoClientFactory {

    @Bean(preDestroy = "close")
    @Singleton
    @Primary
    MongoClient mongoClient(@Value("particle.mongo.uri") String uri) {
        return MongoClients.create(uri);
    }
}
