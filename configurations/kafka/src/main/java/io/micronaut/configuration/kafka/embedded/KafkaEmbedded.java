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

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
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
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.utils.MockTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

    private final KafkaEmbeddedConfiguration embeddedConfiguration;
    private final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Construct a new instance.
     *
     * @param embeddedConfiguration The {@link KafkaEmbeddedConfiguration}
     */
    public KafkaEmbedded(KafkaEmbeddedConfiguration embeddedConfiguration) {
        this.embeddedConfiguration = embeddedConfiguration;
    }

    @Override
    public synchronized AbstractKafkaConfiguration onCreated(BeanCreatedEvent<AbstractKafkaConfiguration> event) {

        AbstractKafkaConfiguration config = event.getBean();
        if (kafkaServer != null) {
            return config;
        }

        String bootstrapServer = config.getConfig().getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);

        String[] hostAndPort = bootstrapServer.split(",")[0].split(":");
        int kafkaPort = -1;
        if (hostAndPort.length == 2) {
            try {
                kafkaPort = Integer.parseInt(hostAndPort[1]);
            } catch (NumberFormatException e) {
                return config;
            }
        } else if (SocketUtils.isTcpPortAvailable(AbstractKafkaConfiguration.DEFAULT_KAFKA_PORT)) {
            kafkaPort = AbstractKafkaConfiguration.DEFAULT_KAFKA_PORT;
        }

        // only handle localhost
        if (embeddedConfiguration.isEnabled() &&
                kafkaServer == null &&
                kafkaPort > -1 &&
                SocketUtils.isTcpPortAvailable(kafkaPort) &&
                init.compareAndSet(false, true)) {
            try {
                if (zkServer == null) {
                    initZooKeeper();
                }

                // setup Broker
                Properties brokerProps = embeddedConfiguration.getProperties();
                String zkConnect = "127.0.0.1:" + zkServer.port();

                brokerProps.setProperty("zookeeper.connect", zkConnect);
                brokerProps.putIfAbsent("broker.id", "0");
                brokerProps.put("port", kafkaPort);
                brokerProps.putIfAbsent("offsets.topic.replication.factor" , "1");

                brokerProps.computeIfAbsent("log.dirs", o -> {
                    try {
                        return Files.createTempDirectory("kafka-").toAbsolutePath().toString();
                    } catch (IOException e) {
                        throw new ConfigurationException("Error creating log directory for embedded Kafka server: " + e.getMessage(), e);
                    }
                });

                brokerProps.setProperty(
                        "listeners",
                        "PLAINTEXT://127.0.0.1:" + kafkaPort
                );
                KafkaConfig kafkaConfig = new KafkaConfig(brokerProps);
                this.kafkaServer = TestUtils.createServer(kafkaConfig, new MockTime());

                List<String> topics = embeddedConfiguration.getTopics();

                if (!topics.isEmpty()) {
                    Properties properties = new Properties();
                    properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, ("127.0.0.1:" + kafkaPort));
                    AdminClient adminClient = AdminClient.create(properties);
                    adminClient.createTopics(topics.stream().map(s -> new NewTopic(s, 1, (short) 1)).collect(Collectors.toList()))
                               .all().get();
                }
            } catch (Throwable e) {
                // check server not already running
                if (!e.getMessage().contains("Address already in use")) {
                    throw new ConfigurationException("Error starting embedded Kafka server: " + e.getMessage(), e);
                }
            }
        }
        return config;
    }

    @Override
    @PreDestroy
    public void close() {
        new Thread(() -> {
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
        }, "embedded-kafka-shutdown-thread").start();

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
