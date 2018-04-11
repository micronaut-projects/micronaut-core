/**
 * Configuration for RabbitMQ
 */
@Configuration
@Requires(classes = {Channel.class, Connection.class, ConnectionFactory.class})
package io.micronaut.configuration.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;