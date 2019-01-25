package io.micronaut.configuration.rabbitmq.intercept;

import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;

public interface DefaultConsumer extends Consumer {

    @Override
    default void handleConsumeOk(String consumerTag) { }

    @Override
    default void handleCancelOk(String consumerTag) { handleTerminate(consumerTag); }

    @Override
    default void handleCancel(String consumerTag) throws IOException { handleTerminate(consumerTag); }

    @Override
    default void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) { handleTerminate(consumerTag); }

    @Override
    default void handleRecoverOk(String consumerTag) { }

    void handleTerminate(String consumerTag);
}
