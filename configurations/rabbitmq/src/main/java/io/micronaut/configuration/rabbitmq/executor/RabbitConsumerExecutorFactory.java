package io.micronaut.configuration.rabbitmq.executor;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorConfiguration;
import io.micronaut.scheduling.executor.ExecutorType;
import io.micronaut.scheduling.executor.UserExecutorConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Configures a {@link java.util.concurrent.ScheduledExecutorService} for running {@link io.micronaut.configuration.rabbitmq.annotation.RabbitListener} instances.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Requires(missingProperty = ExecutorConfiguration.PREFIX_CONSUMER)
@Factory
public class RabbitConsumerExecutorFactory {

    /**
     * @return The executor configurations
     */
    @Singleton
    @Bean
    @Named(TaskExecutors.MESSAGE_CONSUMER)
    ExecutorConfiguration executor() {
        return UserExecutorConfiguration.of(ExecutorType.FIXED, 75);
    }

}
