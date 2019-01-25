package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.messaging.annotation.Header;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class RabbitHeaderBinder implements RabbitAnnotatedArgumentBinder<Header> {

    private final ConversionService conversionService;

    public RabbitHeaderBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<Header> getAnnotationType() {
        return Header.class;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, RabbitMessageState source) {
        String parameterName = context.getAnnotationMetadata().getValue(Header.class, String.class).orElse(context.getArgument().getName());
        return () -> Optional.ofNullable(source.getProperties().getHeaders().get(parameterName))
                .flatMap(prop -> conversionService.convert(prop, context));
    }
}
