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
package org.particleframework.configuration.neo4j.bolt;

import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.exceptions.ConfigurationException;

import javax.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Default factory for creating the Neo4j {@link Driver}
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class Neo4jDriverFactory {


    @Singleton
    @Bean(preDestroy = "close")
    public Driver boltDriver(Neo4jBoltConfiguration configuration) {
        List<URI> uris = configuration.getUris();
        Optional<AuthToken> authToken = configuration.getAuthToken();
        if(uris.size() == 1) {
            return GraphDatabase.driver(
                    uris.get(0),
                    authToken.orElse(AuthTokens.none()),
                    configuration.getConfig()
            );
        }
        else if(!uris.isEmpty()) {
            return GraphDatabase.routingDriver(
                    uris,
                    authToken.orElse(AuthTokens.none()),
                    configuration.getConfig()
            );
        }
        else {
            throw new ConfigurationException("At least one Neo4j URI should be specified eg. neo4j.uri=" + Neo4jBoltConfiguration.DEFAULT_URI);
        }
    }
}
