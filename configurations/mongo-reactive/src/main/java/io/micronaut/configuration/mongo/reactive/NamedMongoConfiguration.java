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
package io.micronaut.configuration.mongo.reactive;

import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.*;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Creates a named configuration for each entry under {@link MongoSettings#MONGODB_SERVERS}
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(value = MongoSettings.MONGODB_SERVERS)
public class NamedMongoConfiguration extends AbstractMongoConfiguration {

    private final String serverName;
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


    public NamedMongoConfiguration(@Parameter String serverName, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.serverName = serverName;
    }

    /**
     * @return The name of the server
     */
    public String getServerName() {
        return serverName;
    }

    @Override
    public ClusterSettings.Builder getClusterSettings() {
        return clusterSettings;
    }

    @Override
    public MongoClientSettings.Builder getClientSettings() {
        return clientSettings;
    }

    @Override
    public ServerSettings.Builder getServerSettings() {
        return serverSettings;
    }

    @Override
    public ConnectionPoolSettings.Builder getPoolSettings() {
        return poolSettings;
    }

    @Override
    public SocketSettings.Builder getSocketSettings() {
        return socketSettings;
    }

    @Override
    public SslSettings.Builder getSslSettings() {
        return sslSettings;
    }

    @Override
    protected String getApplicationName() {
        return applicationConfiguration.getName().map(n -> n + "-" +  serverName).orElse(Environment.DEFAULT_NAME + "-" + serverName);
    }
}
