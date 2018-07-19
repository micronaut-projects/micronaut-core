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

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.netty.NettyStreamFactoryFactory;
import com.mongodb.reactivestreams.client.MongoClients;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;
import org.bson.codecs.pojo.PojoCodecProvider;

import javax.validation.constraints.NotBlank;
import java.util.Optional;

/**
 * Abstract Mongo configuration type.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractReactiveMongoConfiguration {

    private String uri;

    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    protected AbstractReactiveMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * @return The MongoDB URI
     */
    @NotBlank
    public String getUri() {
        return uri;
    }

    /**
     * Sets the MongoDB URI.
     *
     * @param uri The MongoDB URI
     */
    public void setUri(String uri) {
        this.uri = uri;
        Optional<ConnectionString> connectionString = getConnectionString();
        if (connectionString.isPresent()) {
            ConnectionString cs = connectionString.get();
            String streamType = cs.getStreamType();
            if ("netty".equalsIgnoreCase(streamType)) {
                getClientSettings().streamFactoryFactory(NettyStreamFactoryFactory.builder().build());
            }
            getClientSettings().applyConnectionString(cs);
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
        if (StringUtils.isNotEmpty(uri)) {
            return Optional.of(new ConnectionString(uri));
        }
        return Optional.empty();
    }

    /**
     * @return The {@link ClusterSettings#builder()}
     */
    public abstract ClusterSettings.Builder getClusterSettings();

    /**
     * @return The {@link MongoClientSettings#builder()}
     */
    public abstract MongoClientSettings.Builder getClientSettings();

    /**
     * @return The {@link ServerSettings#builder()}
     */
    public abstract ServerSettings.Builder getServerSettings();

    /**
     * @return The {@link ConnectionPoolSettings#builder()}
     */
    public abstract ConnectionPoolSettings.Builder getPoolSettings();

    /**
     * @return The {@link SocketSettings#builder()}
     */
    public abstract SocketSettings.Builder getSocketSettings();

    /**
     * @return The {@link SslSettings#builder()}
     */
    public abstract SslSettings.Builder getSslSettings();

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
        clientSettings.applicationName(getApplicationName());
        clientSettings.applyToClusterSettings(builder -> builder.applySettings(clusterSettings.build()));
        clientSettings.applyToServerSettings(builder -> builder.applySettings(serverSettings.build()));
        clientSettings.applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings.build()));
        clientSettings.applyToSocketSettings(builder -> builder.applySettings(socketSettings.build()));
        clientSettings.applyToSslSettings(builder -> builder.applySettings(sslSettings.build()));

        clientSettings.codecRegistry(
            fromRegistries(MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()))

        );
        return clientSettings.build();
    }

    /**
     * Return the appplication name or a default name.
     * @return applicationName
     */
    protected String getApplicationName() {
        return applicationConfiguration.getName().orElse(Environment.DEFAULT_NAME);
    }
}
