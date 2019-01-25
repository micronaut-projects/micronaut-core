package io.micronaut.configuration.rabbitmq.bind;

import com.rabbitmq.client.AMQP;
import io.micronaut.configuration.rabbitmq.annotation.RabbitProperty;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class RabbitPropertyBinder implements RabbitAnnotatedArgumentBinder<RabbitProperty> {

    private final Map<String, Function<AMQP.BasicProperties, Object>> properties = new HashMap<>();
    private final ConversionService conversionService;

    public RabbitPropertyBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
        properties.put("contentType", AMQP.BasicProperties::getContentType);
        properties.put("contentEncoding", AMQP.BasicProperties::getContentEncoding);
        properties.put("deliveryMode", AMQP.BasicProperties::getDeliveryMode);
        properties.put("priority", AMQP.BasicProperties::getPriority);
        properties.put("correlationId", AMQP.BasicProperties::getCorrelationId);
        properties.put("replyTo", AMQP.BasicProperties::getReplyTo);
        properties.put("expiration", AMQP.BasicProperties::getExpiration);
        properties.put("messageId", AMQP.BasicProperties::getMessageId);
        properties.put("timestamp", AMQP.BasicProperties::getTimestamp);
        properties.put("type", AMQP.BasicProperties::getType);
        properties.put("userId", AMQP.BasicProperties::getUserId);
        properties.put("appId", AMQP.BasicProperties::getAppId);
        properties.put("clusterId", AMQP.BasicProperties::getClusterId);
    }

    @Override
    public Class<RabbitProperty> getAnnotationType() {
        return RabbitProperty.class;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, RabbitMessageState source) {
        String parameterName = context.getAnnotationMetadata().getValue(RabbitProperty.class, String.class).orElse(context.getArgument().getName());

        return () -> Optional.ofNullable(properties.get(parameterName))
                .map(f -> f.apply(source.getProperties()))
                .flatMap(prop -> conversionService.convert(prop, context));
    }
}
