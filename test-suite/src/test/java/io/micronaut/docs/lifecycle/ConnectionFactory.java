package io.micronaut.docs.lifecycle;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
public class ConnectionFactory {
    @Bean(preDestroy = "stop")
    @Singleton
    public Connection connection() {
        return new Connection();
    }

}
