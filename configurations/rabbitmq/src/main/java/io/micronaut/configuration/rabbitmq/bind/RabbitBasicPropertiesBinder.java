package io.micronaut.configuration.rabbitmq.bind;

import com.rabbitmq.client.BasicProperties;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class RabbitBasicPropertiesBinder implements RabbitTypeArgumentBinder<BasicProperties> {

    @Override
    public Argument<BasicProperties> argumentType() {
        return Argument.of(BasicProperties.class);
    }

    @Override
    public BindingResult<BasicProperties> bind(ArgumentConversionContext<BasicProperties> context, RabbitMessageState source) {
        return () -> Optional.of(source.getProperties());
    }
}
