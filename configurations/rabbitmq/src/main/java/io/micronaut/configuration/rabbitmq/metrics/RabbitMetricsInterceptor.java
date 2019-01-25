package io.micronaut.configuration.rabbitmq.metrics;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

public class RabbitMetricsInterceptor implements BeanCreatedEventListener<ConnectionFactory> {

    @Override
    public ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        //event.getBean().setMetricsCollector(new MicrometerMetricsCollector());
        return event.getBean();
    }
}
