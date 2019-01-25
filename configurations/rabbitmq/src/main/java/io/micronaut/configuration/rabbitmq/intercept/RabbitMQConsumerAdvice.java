package io.micronaut.configuration.rabbitmq.intercept;

import com.rabbitmq.client.*;
import io.micronaut.configuration.rabbitmq.annotation.Queue;
import io.micronaut.configuration.rabbitmq.annotation.RabbitListener;
import io.micronaut.configuration.rabbitmq.bind.RabbitBinderRegistry;
import io.micronaut.configuration.rabbitmq.bind.RabbitMessageState;
import io.micronaut.configuration.rabbitmq.connect.ChannelPool;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingClientException;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

@Singleton
public class RabbitMQConsumerAdvice implements ExecutableMethodProcessor<RabbitListener>, AutoCloseable {

    private final BeanContext beanContext;
    private final ChannelPool channelPool;
    private final RabbitBinderRegistry binderRegistry;
    private final List<Channel> consumerChannels = new ArrayList<>();

    public RabbitMQConsumerAdvice(BeanContext beanContext,
                                  ChannelPool channelPool,
                                  RabbitBinderRegistry binderRegistry) {
        this.beanContext = beanContext;
        this.channelPool = channelPool;
        this.binderRegistry = binderRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {

        Optional<AnnotationValue<Queue>> queueAnn = method.findAnnotation(Queue.class);
        String queue = queueAnn.map(ann -> ann.getRequiredValue(String.class)).orElseThrow(() -> new MessageListenerException("No queue specified for method " + method));

        String clientTag = method.getDeclaringType().getSimpleName() + '#' + method.toString();

        boolean reQueue = queueAnn.get().getRequiredValue("reQueue", boolean.class);

        Channel channel = getChannel();

        consumerChannels.add(channel);

        Map<String, Object> arguments = new HashMap<>();

        method.getAnnotationValuesByType(Property.class).forEach((prop) -> {
            String name = prop.getRequiredValue("name", String.class);
            String value = prop.getValue(String.class).orElse(null);

            if (StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)) {
                arguments.put(name, value);
            }
        });
        
        io.micronaut.context.Qualifier<Object> qualifer = beanDefinition
                .getAnnotationTypeByStereotype(Qualifier.class)
                .map(type -> Qualifiers.byAnnotation(beanDefinition, type))
                .orElse(null);

        Class<Object> beanType = (Class<Object>) beanDefinition.getBeanType();

        Object bean = beanContext.findBean(beanType, qualifer).orElseThrow(() -> new MessageListenerException("Could not find the bean to execute the method " + method));

        try {
            DefaultExecutableBinder<RabbitMessageState> binder = new DefaultExecutableBinder<>();

            channel.basicConsume(queue, false, clientTag, false, false, arguments, new DefaultConsumer() {

                @Override
                public void handleTerminate(String consumerTag) {
                    channelPool.returnChannel(channel);
                    consumerChannels.remove(channel);
                }

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    long deliveryTag = envelope.getDeliveryTag();

                    RabbitMessageState state = new RabbitMessageState(envelope, properties, body);

                    try {
                        ((BoundExecutable) binder.bind(method, binderRegistry, state)).invoke(bean);
                        channel.basicAck(deliveryTag, false);
                    } catch (UnsatisfiedArgumentException e) {
                        channel.basicNack(deliveryTag, false, reQueue);
                        throw e;
                    } catch (Throwable e) {
                        channel.basicNack(deliveryTag, false, reQueue);
                    }

                }
            });
        } catch (IOException e) {
            throw new MessagingClientException("An error occurred subscribing to a queue", e);
        } finally {
            channelPool.returnChannel(channel);
            consumerChannels.remove(channel);
        }

    }

    @Override
    public void close() throws Exception {
        for (Channel channel : consumerChannels) {
            channelPool.returnChannel(channel);
        }
    }


    protected Channel getChannel() {
        try {
            return channelPool.getChannel();
        } catch (IOException e) {
            throw new MessagingClientException("Could not retrieve a channel", e);
        }
    }
}
