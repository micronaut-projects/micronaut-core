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

package io.micronaut.configuration.mongo.reactive.test;

import com.mongodb.ConnectionString;
import com.mongodb.connection.ClusterSettings;
import de.flapdoodle.embed.mongo.MongodProcess;
import io.micronaut.configuration.mongo.reactive.DefaultReactiveMongoConfiguration;
import io.micronaut.configuration.mongo.reactive.MongoSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * This class will configure a {@link MongodProcess} if the class is on the classpath and the server is not configured.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = MongodProcess.class)
@Requires(beans = DefaultReactiveMongoConfiguration.class)
@Requires(env = Environment.TEST)
@Requires(property = MongoSettings.EMBEDDED, notEquals = "false", defaultValue = "true")
@Singleton
public class ReactiveMongoProcessFactory extends AbstractMongoProcessFactory implements BeanCreatedEventListener<DefaultReactiveMongoConfiguration>, Closeable {

    @Override
    public DefaultReactiveMongoConfiguration onCreated(BeanCreatedEvent<DefaultReactiveMongoConfiguration> event) {
        DefaultReactiveMongoConfiguration configuration = event.getBean();
        try {
            Optional<ConnectionString> connectionString = configuration.getConnectionString();
            ClusterSettings.Builder clusterSettings = configuration.getClusterSettings();
            startEmbeddedMongoIfPossible(connectionString.orElse(null), clusterSettings);
        } catch (IOException e) {
            throw new ConfigurationException("Error starting Embedded MongoDB server: " + e.getMessage(), e);
        }
        return configuration;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if (process != null) {
            process.stop();
        }
    }
}
