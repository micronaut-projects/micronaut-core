package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.bind.ArgumentBinder;

public interface RabbitArgumentBinder<T> extends ArgumentBinder<T, RabbitMessageState> {
}
