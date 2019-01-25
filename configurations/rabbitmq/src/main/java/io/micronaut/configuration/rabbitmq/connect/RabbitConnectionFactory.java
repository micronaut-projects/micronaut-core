package io.micronaut.configuration.rabbitmq.connect;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.scheduling.TaskExecutors;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

@Factory
public class RabbitConnectionFactory {

    @Bean(preDestroy = "close")
    @Singleton
    Connection connection(ConnectionFactory connectionFactory,
                          @Named(TaskExecutors.MESSAGE_CONSUMER) ExecutorService executorService) {
        try {
            return connectionFactory.newConnection(executorService);
        } catch (IOException | TimeoutException e) {
            throw new BeanInstantiationException("Error creating connection to RabbitMQ", e);
        }
    }
}
