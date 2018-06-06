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

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * The default MongoDB configuration class.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(property = MongoSettings.PREFIX)
@Requires(missingProperty = MongoSettings.MONGODB_SERVERS)
@ConfigurationProperties(MongoSettings.PREFIX)
public class DefaultReactiveMongoConfiguration extends AbstractReactiveMongoConfiguration {

    @ConfigurationBuilder(prefixes = "")
    protected MongoClientSettings.Builder clientSettings = MongoClientSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "cluster")
    protected ClusterSettings.Builder clusterSettings = ClusterSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "server")
    protected ServerSettings.Builder serverSettings = ServerSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "connectionPool")
    protected ConnectionPoolSettings.Builder poolSettings = ConnectionPoolSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "socket")
    protected SocketSettings.Builder socketSettings = SocketSettings.builder();

    @ConfigurationBuilder(prefixes = "", configurationPrefix = "ssl")
    protected SslSettings.Builder sslSettings = SslSettings.builder();

    /**
     * Constructor.
     * @param applicationConfiguration applicationConfiguration
     */
    public DefaultReactiveMongoConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }


    /**
     * Sets the server MongoDB server address.
     *
     * @param serverAddress The server address
     */
    public void setHost(ServerAddress serverAddress) {
        getClusterSettings().hosts(Collections.singletonList(serverAddress));
    }

    /**
     * Sets the server MongoDB server address.
     *
     * @param serverAddresses The server addresses
     */
    public void setHosts(List<ServerAddress> serverAddresses) {
        if (serverAddresses != null) {
            getClusterSettings().hosts(serverAddresses);
        }
    }

    /**
     * @return The {@link ClusterSettings#builder()}
     */
    @Override
    public ClusterSettings.Builder getClusterSettings() {
        return clusterSettings;
    }

    /**
     * @return The {@link MongoClientSettings#builder()}
     */
    @Override
    public MongoClientSettings.Builder getClientSettings() {
        return clientSettings;
    }

    /**
     * @return The {@link ServerSettings#builder()}
     */
    @Override
    public ServerSettings.Builder getServerSettings() {
        return serverSettings;
    }

    /**
     * @return The {@link ConnectionPoolSettings#builder()}
     */
    @Override
    public ConnectionPoolSettings.Builder getPoolSettings() {
        return poolSettings;
    }

    /**
     * @return The {@link SocketSettings#builder()}
     */
    @Override
    public SocketSettings.Builder getSocketSettings() {
        return socketSettings;
    }

    /**
     * @return The {@link SslSettings#builder()}
     */
    @Override
    public SslSettings.Builder getSslSettings() {
        return sslSettings;
    }

    @Override
    public String toString() {
        return "DefaultReactiveMongoConfiguration{" +
            "uri='" + getUri() + '\'' +
            ", clientSettings=" + clientSettings +
            ", clusterSettings=" + clusterSettings +
            '}';
    }
}
