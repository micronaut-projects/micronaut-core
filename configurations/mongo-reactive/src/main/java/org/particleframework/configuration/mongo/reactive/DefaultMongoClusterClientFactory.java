package org.particleframework.configuration.mongo.reactive;

import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.particleframework.context.annotation.*;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows connecting to a Mongo cluster via the the {@code "particle.mongo.uris"} setting
 *
 * @author James Kleeh
 * @since 1.0
 */
@Requires(property = "particle.mongo.uris")
@Requires(missingProperty = "particle.mongo.uri")
@Factory
public class DefaultMongoClusterClientFactory {

    @Bean(preDestroy = "close")
    @Singleton
    @Primary
    MongoClient mongoClient(@Value("particle.mongo.uris") String... uris) {
        List<ServerAddress> addresses = Arrays.stream(uris).map(ServerAddress::new).collect(Collectors.toList());
        ClusterSettings clusterSettings = ClusterSettings.builder().hosts(addresses).build();
        MongoClientSettings settings = MongoClientSettings.builder().clusterSettings(clusterSettings).build();
        return MongoClients.create(settings);
    }
}