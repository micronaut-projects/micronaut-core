package io.micronaut.configuration.rabbitmq.bind;

import com.rabbitmq.client.Envelope;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class RabbitEnvelopeBinder implements RabbitTypeArgumentBinder<Envelope> {

    @Override
    public Argument<Envelope> argumentType() {
        return Argument.of(Envelope.class);
    }

    @Override
    public BindingResult<Envelope> bind(ArgumentConversionContext<Envelope> context, RabbitMessageState source) {
        return () -> Optional.of(source.getEnvelope());
    }
}
