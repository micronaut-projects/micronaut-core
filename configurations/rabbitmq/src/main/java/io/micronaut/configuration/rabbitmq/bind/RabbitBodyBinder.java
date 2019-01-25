package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.messaging.annotation.Body;

import javax.inject.Singleton;

@Singleton
public class RabbitBodyBinder implements RabbitAnnotatedArgumentBinder<Body> {

    private final ConversionService conversionService;

    public RabbitBodyBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, RabbitMessageState source) {
        return () -> conversionService.convert(source.getBody(), context);
    }
}
