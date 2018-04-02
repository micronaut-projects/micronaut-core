package io.micronaut.configuration.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.micronaut.context.annotation.*;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Factory
@Requires(classes = {Channel.class, Connection.class, ConnectionFactory.class})
public class RabbitFactory {

    @Bean
    @Singleton
    public ConnectionFactory connectionFactory() {
        return new ConnectionFactory();
    }

    @Bean(preDestroy = "close")
    public Connection connection(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        return connectionFactory.newConnection();
    }

    @Bean(preDestroy = "close")
    public Channel channel(Connection connection) throws IOException {
        return connection.createChannel();
    }
}
