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
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Logger;
import org.particleframework.configuration.neo4j.bolt.condition.RequiresNeo4j;
import org.particleframework.context.annotation.ConfigurationBuilder;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.util.StringUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for Bolt Neo4j driver
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(Neo4jBoltSettings.PREFIX)
@RequiresNeo4j
public class Neo4jBoltConfiguration implements Neo4jBoltSettings {

    private List<URI> uris = Collections.singletonList(URI.create(DEFAULT_URI));

    @ConfigurationBuilder(prefixes = "with", allowZeroArgs = true)
    protected Config.ConfigBuilder config = Config.build();

    private AuthToken authToken;
    private String username;
    private String password;

    public Neo4jBoltConfiguration() {
        config.withLogging(name -> new Logger() {
            org.slf4j.Logger logger = LoggerFactory.getLogger(name);
            @Override
            public void error(String message, Throwable cause) {
                logger.error(message, cause);
            }

            @Override
            public void info(String message, Object... params) {
                logger.info(message, params);
            }

            @Override
            public void warn(String message, Object... params) {
                logger.warn(message,params);
            }

            @Override
            public void warn(String message, Throwable cause) {
                logger.warn(message,cause);
            }

            @Override
            public void debug(String message, Object... params) {
                logger.debug(message, params);
            }

            @Override
            public void trace(String message, Object... params) {
                logger.trace(message, params);
            }

            @Override
            public boolean isTraceEnabled() {
                return logger.isTraceEnabled();
            }

            @Override
            public boolean isDebugEnabled() {
                return logger.isDebugEnabled();
            }
        });
    }

    public void setUris(List<URI> uris) {
        if(uris != null) {
            this.uris = uris;
        }
    }

    public void setUri(URI uri) {
        if(uri != null) {
            this.uris = Collections.singletonList(uri);
        }
    }

    /**
     * @return The Neo4j URIs
     */
    public List<URI> getUris() {
        return uris;
    }

    /**
     * @return The configuration
     */
    public Config getConfig() {
        return config.toConfig();
    }

    /**
     * @return The configuration builder used
     */
    public Config.ConfigBuilder getConfigBuilder() {
        return config;
    }

    /**
     * @return The auth token to use
     * @see org.neo4j.driver.v1.AuthTokens
     */
    public Optional<AuthToken> getAuthToken() {
        if(authToken != null) {
            return Optional.of(authToken);
        }
        else if(StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            return Optional.of(AuthTokens.basic(username, password));
        }
        return Optional.empty();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Inject
    public void setTrustStrategy(@Nullable Config.TrustStrategy trustStrategy) {
        if(trustStrategy != null) {
            this.config.withTrustStrategy(trustStrategy);
        }
    }

    @Inject
    public void setAuthToken(@Nullable AuthToken authToken) {
        this.authToken = authToken;
    }
}
