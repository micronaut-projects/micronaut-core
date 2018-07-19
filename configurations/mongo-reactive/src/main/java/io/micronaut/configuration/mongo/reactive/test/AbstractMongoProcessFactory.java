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
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.micronaut.core.io.socket.SocketUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Abstract process factory implementation for embedding MongoDB.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractMongoProcessFactory {
    MongodProcess process;

    /**
     * Starts a MongoDB process if possible.
     *
     * @param connectionString The optional connection string
     * @param clusterSettings The optional cluster settings
     * @throws IOException If an error occurs starting the process
     */
    void startEmbeddedMongoIfPossible(
            @Nullable ConnectionString connectionString,
            @Nullable ClusterSettings.Builder clusterSettings) throws IOException {
        if (connectionString != null) {
            String first = connectionString.getHosts().get(0);
            int port = new ServerAddress(first).getPort();
            if (SocketUtils.isTcpPortAvailable(port)) {
                startMongoProcess(port);
            }
        } else if (clusterSettings != null) {
            ClusterSettings settings = clusterSettings.build();
            List<ServerAddress> hosts = settings.getHosts();
            if (hosts.size() == 1) {
                int port = hosts.get(0).getPort();
                if (SocketUtils.isTcpPortAvailable(port)) {
                    startMongoProcess(port);
                }
            }
        }
    }

    private void startMongoProcess(int port) throws IOException {
        IMongodConfig mongodConfig = new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net("localhost", port, Network.localhostIsIPv6()))
            .build();

        MongodExecutable mongodExecutable = MongodStarter.getDefaultInstance().prepare(mongodConfig);
        this.process = mongodExecutable.start();
    }
}
