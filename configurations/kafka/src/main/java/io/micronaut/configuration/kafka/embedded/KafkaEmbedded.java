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

package io.micronaut.configuration.kafka.embedded;

import io.micronaut.configuration.kafka.AbstractKafkaConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.socket.SocketUtils;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.utils.MockTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Properties;

/**
 * This class will configure a Kafka server for the test environment if no server is already available.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(env = {Environment.TEST, Environment.DEVELOPMENT})
@Requires(classes = {KafkaServer.class, ZkClient.class, TestUtils.class, org.apache.kafka.test.TestUtils.class})
@Requires(property = AbstractKafkaConfiguration.EMBEDDED)
public class KafkaEmbedded implements BeanCreatedEventListener<AbstractKafkaConfiguration>, AutoCloseable {

    private static final String ZKHOST = "127.0.0.1";
    private static final Logger LOG = LoggerFactory.getLogger(KafkaEmbedded.class);

    private EmbeddedZookeeper zkServer;
    private ZkClient zkClient;
    private ZkUtils zkUtils;
    private KafkaServer kafkaServer;


    @Override
    public AbstractKafkaConfiguration onCreated(BeanCreatedEvent<AbstractKafkaConfiguration> event) {

        AbstractKafkaConfiguration config = event.getBean();

        String bootstrapServer = config.getConfig().getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);


        // only handle localhost
        if (kafkaServer == null &&
                AbstractKafkaConfiguration.DEFAULT_BOOTSTRAP_SERVERS.equals(bootstrapServer) &&
                SocketUtils.isTcpPortAvailable(AbstractKafkaConfiguration.DEFAULT_KAFKA_PORT)) {
            try {
                if (zkServer == null) {
                    initZooKeeper();
                }

// setup Broker
                Properties brokerProps = new Properties();
                String zkConnect = "localhost:" + zkServer.port();
                brokerProps.setProperty("zookeeper.connect", zkConnect);
                brokerProps.setProperty("broker.id", "0");
                brokerProps.setProperty("log.dirs", Files.createTempDirectory("kafka-").toAbsolutePath().toString());
                brokerProps.setProperty("listeners", "PLAINTEXT://localhost:" + AbstractKafkaConfiguration.DEFAULT_KAFKA_PORT);
                brokerProps.setProperty("offsets.topic.replication.factor" , "1");
                KafkaConfig kafkaConfig = new KafkaConfig(brokerProps);
                this.kafkaServer = TestUtils.createServer(kafkaConfig, new MockTime());
            } catch (Throwable e) {
                throw new ConfigurationException("Error starting embedded Kafka server: " + e.getMessage(), e);
            }
        }
        return config;
    }

    @Override
    @PreDestroy
    public void close() {
        if (kafkaServer != null) {
            try {
                kafkaServer.shutdown();
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down embedded Kafka Server: " + e.getMessage(), e);
                }
            }
        }
        if (zkClient != null) {
            try {
                zkClient.close();
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down embedded ZooKeeper Client: " + e.getMessage(), e);
                }
            }
        }
        if (zkServer != null) {
            try {
                zkServer.shutdown();
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down embedded ZooKeeper: " + e.getMessage(), e);
                }
            }
        }
    }


    /**
     * Return the configured Kafka server is it was configured.
     *
     * @return An optional {@link KafkaServer}
     */
    public Optional<KafkaServer> getKafkaServer() {
        return Optional.ofNullable(kafkaServer);
    }

    /**
     * Returns the Zookeeper tools if they are available.
     *
     * @return The Zookeeper tools
     */
    public Optional<ZkUtils> getZkUtils() {
        return Optional.ofNullable(zkUtils);
    }

    private void initZooKeeper() {
        zkServer = new EmbeddedZookeeper();
        String zkConnect = ZKHOST + ":" + zkServer.port();
        this.zkClient = new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer$.MODULE$);
        this.zkUtils = ZkUtils.apply(zkClient, false);
    }
}
