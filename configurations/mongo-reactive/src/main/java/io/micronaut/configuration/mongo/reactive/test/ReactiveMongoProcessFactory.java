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
package io.micronaut.configuration.mongo.reactive.test;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.micronaut.configuration.mongo.reactive.ReactiveMongoConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.socket.SocketUtils;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * This class will configure a {@link MongodProcess} if the class is on the classpath and the server is not configured
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = MongodProcess.class)
@Requires(beans = ReactiveMongoConfiguration.class)
@Requires(env = Environment.TEST)
@Singleton
public class ReactiveMongoProcessFactory implements BeanCreatedEventListener<ReactiveMongoConfiguration>, Closeable {

    private MongodProcess process;

    @Override
    public ReactiveMongoConfiguration onCreated(BeanCreatedEvent<ReactiveMongoConfiguration> event) {
        ReactiveMongoConfiguration configuration = event.getBean();
        try {
            Optional<ConnectionString> connectionString = configuration.getConnectionString();
            if(connectionString.isPresent()) {
                String first = connectionString.get().getHosts().get(0);
                if(SocketUtils.isTcpPortAvailable(new ServerAddress(first).getPort())) {

                    // should be ok to do this without checking unless MongoNotAvailableCondition is not working properly
                    int port = new ServerAddress(first).getPort();
                    IMongodConfig mongodConfig = new MongodConfigBuilder()
                            .version(Version.Main.PRODUCTION)
                            .net(new Net("localhost", port, Network.localhostIsIPv6()))
                            .build();

                    MongodExecutable mongodExecutable = MongodStarter.getDefaultInstance().prepare(mongodConfig);
                    this.process = mongodExecutable.start();
                }
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error starting Embedded MongoDB server: " + e.getMessage(), e);
        }
        return configuration;
    }


    @Override
    @PreDestroy
    public void close() throws IOException {
        if(process != null) {
            process.stop();
        }
    }
}
