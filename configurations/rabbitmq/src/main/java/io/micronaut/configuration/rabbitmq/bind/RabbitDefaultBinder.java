package io.micronaut.configuration.rabbitmq.bind;


import io.micronaut.configuration.rabbitmq.annotation.RabbitProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class RabbitDefaultBinder implements RabbitArgumentBinder<Object> {

    private final RabbitPropertyBinder propertyBinder;
    private final ConversionService conversionService;

    public RabbitDefaultBinder(RabbitPropertyBinder propertyBinder,
                               ConversionService conversionService) {
        this.propertyBinder = propertyBinder;
        this.conversionService = conversionService;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, RabbitMessageState source) {

        BindingResult<Object> result = propertyBinder.bind(context, source);
        if (result.isPresentAndSatisfied()) {
            return result;
        }

        Object value = source.getBody();
        return () -> conversionService.convert(value, context);
    }
}
