package io.micronaut.configuration.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * The default RabbitMQ configuration class
 *
 * Allows RabbitMQ client to leverage Micronaut properties configuration
 *
 * @author benrhine
 * @since 1.0
 */
@Singleton
@Requires(classes = {ConnectionFactory.class})
@ConfigurationProperties("rabbitmq")
public class RabbitConnectionFactoryConfig extends ConnectionFactory {

}
