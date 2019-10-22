package io.micronaut.docs.lifecycle;

// tag::class[]
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
public class ConnectionFactory {

    @Bean(preDestroy = "stop") // <1>
    @Singleton
    public Connection connection() {
        return new Connection();
    }
}
// end::class[]
