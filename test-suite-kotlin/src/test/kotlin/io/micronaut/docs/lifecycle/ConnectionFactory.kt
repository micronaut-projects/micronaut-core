package io.micronaut.docs.lifecycle

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

import javax.inject.Singleton

@Factory
class ConnectionFactory {
    @Bean(preDestroy = "stop")
    @Singleton
    fun connection(): Connection {
        return Connection()
    }

}
