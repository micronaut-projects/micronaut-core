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
package org.particleframework.configuration.mongo.reactive;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.*;
import com.mongodb.reactivestreams.client.MongoClients;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.particleframework.core.util.StringUtils;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Abstract Mongo configuration type
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractMongoConfiguration {

    protected String uri;

    /**
     * @return The MongoDB URI
     */
    @NotBlank
    public String getUri() {
        return uri;
    }

    /**
     * @return The {@link ClusterSettings#builder()}
     */
    public abstract ClusterSettings.Builder getClusterSettings();

    /**
     * @return The {@link MongoClientSettings#builder()}
     */
    public abstract MongoClientSettings.Builder getClientSettings();

    public abstract ServerSettings.Builder getServerSettings();

    public abstract ConnectionPoolSettings.Builder getPoolSettings();

    public abstract SocketSettings.Builder getSocketSettings();

    public abstract SslSettings.Builder getSslSettings();

    /**
     * Sets the MongoDB URI
     * @param uri The MongoDB URI
     */
    public void setUri(String uri) {
        this.uri = uri;
        Optional<ConnectionString> connectionString = getConnectionString();
        if(connectionString.isPresent()) {
            ConnectionString cs = connectionString.get();

            getServerSettings().applyConnectionString(cs);
            getClusterSettings().applyConnectionString(cs);
            getPoolSettings().applyConnectionString(cs);
            getSslSettings().applyConnectionString(cs);
            getSocketSettings().applyConnectionString(cs);
        }
    }


    /**
     * @return The MongoDB {@link ConnectionString}
     */
    public Optional<ConnectionString> getConnectionString() {
        if(StringUtils.isNotEmpty(uri)) {
            return Optional.of(new ConnectionString(uri));
        }
        return Optional.empty();
    }

    /**
     * @return Builds the {@link MongoClientSettings}
     */
    public MongoClientSettings buildSettings() {
        ClusterSettings.Builder clusterSettings = getClusterSettings();
        SslSettings.Builder sslSettings = getSslSettings();
        ConnectionPoolSettings.Builder poolSettings = getPoolSettings();
        SocketSettings.Builder socketSettings = getSocketSettings();
        ServerSettings.Builder serverSettings = getServerSettings();


        MongoClientSettings.Builder clientSettings = getClientSettings();
        clientSettings.clusterSettings(clusterSettings.build())
                      .serverSettings(serverSettings.build())
                      .connectionPoolSettings(poolSettings.build())
                      .socketSettings(socketSettings.build())
                      .sslSettings(sslSettings.build())  ;

        clientSettings.codecRegistry(
                fromRegistries(MongoClients.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()))

        );
        return clientSettings.build();
    }
}
