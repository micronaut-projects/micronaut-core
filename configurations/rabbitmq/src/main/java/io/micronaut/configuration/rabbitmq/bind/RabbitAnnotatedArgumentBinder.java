package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.messaging.annotation.Header;

import java.lang.annotation.Annotation;

public interface RabbitAnnotatedArgumentBinder<A extends Annotation> extends AnnotatedArgumentBinder<A, Object, RabbitMessageState>, RabbitArgumentBinder<Object>  {
}
