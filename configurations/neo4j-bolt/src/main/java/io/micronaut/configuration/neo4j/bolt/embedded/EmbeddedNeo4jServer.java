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

package io.micronaut.configuration.neo4j.bolt.embedded;

import static org.neo4j.dbms.DatabaseManagementSystemSettings.data_directory;

import io.micronaut.configuration.neo4j.bolt.Neo4jBoltConfiguration;
import io.micronaut.configuration.neo4j.bolt.Neo4jBoltSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.socket.SocketUtils;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilder;
import org.neo4j.harness.TestServerBuilders;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.server.ServerStartupException;

import javax.inject.Singleton;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Starts an embedded Neo4j server if no server is running for the configured settings.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = ServerControls.class)
public class EmbeddedNeo4jServer implements BeanCreatedEventListener<Neo4jBoltConfiguration>, Closeable {
    private static final int RETRY_COUNT_MAX = 4;
    private ServerControls serverControls;

    @Override
    public Neo4jBoltConfiguration onCreated(BeanCreatedEvent<Neo4jBoltConfiguration> event) {
        Neo4jBoltConfiguration configuration = event.getBean();

        List<URI> uris = configuration.getUris();
        if (uris.size() == 1) {
            URI uri = uris.get(0);
            int port = uri.getPort();
            Neo4jBoltConfiguration.Neo4jEmbeddedSettings embeddedSettings = configuration.getEmbeddedSettings();
            if (port > -1 && SocketUtils.isTcpPortAvailable(port) && embeddedSettings.isEnabled()) {
                // run embedded server, since it isn't up
                final String location = embeddedSettings.getDirectory().orElse(null);
                final Map<String, Object> options = embeddedSettings.getOptions();
                final File dataDir;
                try {
                    if (location != null) {
                        dataDir = new File(location);
                    } else if (embeddedSettings.isEphemeral()) {
                        dataDir = File.createTempFile("neo4j-temporary-data", "-tempdir");
                    } else {
                        dataDir = new File(Neo4jBoltSettings.DEFAULT_LOCATION);
                    }
                } catch (IOException e) {
                    throw new ConfigurationException("Unable to create Neo4j temporary data directory: " + e.getMessage(), e);
                }
                if (embeddedSettings.isDropData() || embeddedSettings.isEphemeral()) {
                    dataDir.delete();
                }
                if (embeddedSettings.isEphemeral()) {
                    dataDir.deleteOnExit();
                }
                try {
                    serverControls = start(uri.getHost(), uri.getPort(), dataDir, options);
                    URI boltURI = serverControls.boltURI();
                    configuration.setUri(boltURI);
                } catch (Throwable e) {
                    throw new ConfigurationException("Unable to start embedded Neo4j server: " + e.getMessage(), e);
                }
            }
        }

        return configuration;
    }

    /**
     * Start a server on a random free port.
     *
     * @param dataLocation The data location
     * @return The server controls
     * @throws IOException i/o exception
     */
    public static ServerControls start(File dataLocation) throws IOException {
        return attemptStartServer(0, dataLocation, Collections.<String, Object>emptyMap());
    }

    /**
     * Start a server on a random free port.
     *
     * @param dataLocation The data location
     * @param options options for neo4j
     * @return The server controls
     * @throws IOException i/o exception
     */
    public static ServerControls start(File dataLocation, Map<String, Object> options) throws IOException {
        return attemptStartServer(0, dataLocation, options);
    }

    /**
     * Start a server on the given address.
     *
     * @param inetAddr The inet address
     * @return The {@link ServerControls}
     */
    public static ServerControls start(InetSocketAddress inetAddr) {
        return start(inetAddr.getHostName(), inetAddr.getPort(), null);
    }

    /**
     * Start a server on the given address.
     *
     * @param inetAddr The inet address
     * @param dataLocation dataLocation file
     * @return The {@link ServerControls}
     */
    public static ServerControls start(InetSocketAddress inetAddr, File dataLocation) {
        return start(inetAddr.getHostName(), inetAddr.getPort(), dataLocation);
    }

    /**
     * Start a server on the given address.
     *
     * @param inetAddr The inet address
     * @param dataLocation dataLocation file
     * @param options options for neo4j
     * @return The {@link ServerControls}
     */
    public static ServerControls start(InetSocketAddress inetAddr, File dataLocation, Map<String, Object> options) {
        return start(inetAddr.getHostName(), inetAddr.getPort(), dataLocation, options);
    }

    /**
     * Start a server on the given address.
     *
     * @param address The address
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String address) {
        URI uri = URI.create(address);
        return start(new InetSocketAddress(uri.getHost(), uri.getPort()));
    }

    /**
     * Start a server on the given address.
     *
     * @param address The address
     * @param dataLocation dataLocation file
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String address, File dataLocation) {
        URI uri = URI.create(address);
        return start(uri.getHost(), uri.getPort(), dataLocation);
    }

    /**
     * Start a server on the given address.
     *
     * @param address The address
     * @param options options for neo4j
     * @param dataLocation dataLocation file
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String address, File dataLocation, Map<String, Object> options) {
        URI uri = URI.create(address);
        return start(uri.getHost(), uri.getPort(), dataLocation, options);
    }

    /**
     * Start a server on the given address.
     *
     * @param host The host
     * @param port The port
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String host, int port) {
        return start(host, port, null);
    }

    /**
     * Start a server on the given address.
     *
     * @param host The host
     * @param port The port
     * @param dataLocation dataLocation file
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String host, int port, File dataLocation) {
        return start(host, port, dataLocation, Collections.<String, Object>emptyMap());
    }

    /**
     * Start a server on the given address.
     *
     * @param host The host
     * @param port The port
     * @param dataLocation dataLocation file
     * @param options options for neo4j
     * @return The {@link ServerControls}
     */
    public static ServerControls start(String host, int port, File dataLocation, Map<String, Object> options) {
        String myBoltAddress = String.format("%s:%d", host, port);

        TestServerBuilder serverBuilder = TestServerBuilders.newInProcessBuilder()
            .withConfig(new BoltConnector("0").enabled, "true")
            .withConfig(new BoltConnector("0").type, GraphDatabaseSettings.Connector.ConnectorType.BOLT.name())
            .withConfig(new BoltConnector("0").encryption_level, BoltConnector.EncryptionLevel.DISABLED.name())
            .withConfig(new BoltConnector("0").listen_address, myBoltAddress);
        if (dataLocation != null) {
            serverBuilder = serverBuilder.withConfig(data_directory, dataLocation.getPath());
        }

        for (String name : options.keySet()) {
            serverBuilder.withConfig(name, options.get(name).toString());
        }

        return serverBuilder
            .newServer();
    }

    private static ServerControls attemptStartServer(int retryCount, File dataLocation, Map<String, Object> options) throws IOException {
        try {
            //In the new driver 0 implicitly means a random port
            return start("localhost", 0, dataLocation, options);
        } catch (ServerStartupException sse) {
            if (retryCount < RETRY_COUNT_MAX) {
                return attemptStartServer(++retryCount, dataLocation, options);
            } else {
                throw sse;
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverControls.close();
    }
}
