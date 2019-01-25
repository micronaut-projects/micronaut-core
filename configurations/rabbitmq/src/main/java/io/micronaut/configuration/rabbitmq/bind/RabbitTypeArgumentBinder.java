package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.bind.TypeArgumentBinder;

public interface RabbitTypeArgumentBinder<T> extends TypeArgumentBinder<T, RabbitMessageState>, RabbitArgumentBinder<T> {
}
