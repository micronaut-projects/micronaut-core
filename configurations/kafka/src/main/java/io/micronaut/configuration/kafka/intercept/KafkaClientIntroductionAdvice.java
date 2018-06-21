/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.kafka.intercept;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.kafka.KafkaProducerFactory;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration;
import io.micronaut.configuration.kafka.config.DefaultKafkaProducerConfiguration;
import io.micronaut.configuration.kafka.config.KafkaProducerConfiguration;
import io.micronaut.configuration.kafka.serde.SerdeRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.exceptions.MessagingClientException;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of the {@link io.micronaut.configuration.kafka.annotation.KafkaClient} advice annotation.
 *
 * @author Graeme Rocher
 * @see io.micronaut.configuration.kafka.annotation.KafkaClient
 * @since 1.0
 */
@Singleton
public class KafkaClientIntroductionAdvice implements MethodInterceptor<Object, Object>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientIntroductionAdvice.class);

    private final BeanContext beanContext;
    private final KafkaProducerFactory producerFactory;
    private final SerdeRegistry serdeRegistry;
    private final ConversionService<?> conversionService;
    private final Map<ProducerKey, KafkaProducer> producerMap = new ConcurrentHashMap<>();

    /**
     * Creates the introduction advice for the given arguments.
     *
     * @param beanContext       The bean context.
     * @param producerFactory   The producer factory.
     * @param serdeRegistry     The serde registry
     * @param conversionService The conversion service
     */
    public KafkaClientIntroductionAdvice(
            BeanContext beanContext,
            KafkaProducerFactory producerFactory,
            SerdeRegistry serdeRegistry,
            ConversionService<?> conversionService) {
        this.beanContext = beanContext;
        this.producerFactory = producerFactory;
        this.serdeRegistry = serdeRegistry;
        this.conversionService = conversionService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final Object intercept(MethodInvocationContext<Object, Object> context) {

        if (context.hasAnnotation(Topic.class) && context.hasAnnotation(KafkaClient.class)) {
            KafkaClient client = context.getAnnotation(KafkaClient.class);
            String topic = context.getValue(Topic.class, String.class).orElse(null);

            if (StringUtils.isEmpty(topic)) {
                throw new MessagingClientException("No topic specified for method: " + context);
            }
            Argument bodyArgument = findBodyArgument(context);
            if (bodyArgument == null) {
                throw new MessagingClientException("No valid message body argument found for method: " + context);
            }

            Argument keyArgument = Arrays.stream(context.getArguments())
                    .filter(arg -> arg.getAnnotation(KafkaKey.class) != null)
                    .findFirst().orElse(null);

            String clientId = client.id();

            if (StringUtils.isEmpty(clientId)) {
                clientId = null;
            }

            KafkaProducer kafkaProducer = getProducer(bodyArgument, keyArgument, clientId);

            List<Header> kafkaHeaders = new ArrayList<>();
            io.micronaut.messaging.annotation.Header[] headers = context.getAnnotationsByType(io.micronaut.messaging.annotation.Header.class);

            for (io.micronaut.messaging.annotation.Header header : headers) {
                kafkaHeaders.add(
                        new RecordHeader(
                                header.name(),
                                header.value().getBytes(StandardCharsets.UTF_8)
                        )
                );
            }

            Argument[] arguments = context.getArguments();
            Map<String, Object> parameterValues = context.getParameterValueMap();

            for (Argument argument : arguments) {
                io.micronaut.messaging.annotation.Header headerAnn = argument.getAnnotation(io.micronaut.messaging.annotation.Header.class);
                if (headerAnn != null) {
                    String name = headerAnn.name();
                    if (StringUtils.isEmpty(name)) {
                        name = headerAnn.value();
                    }

                    if (StringUtils.isEmpty(name)) {
                        name = argument.getName();
                    }

                    Object v = parameterValues.get(argument.getName());

                    if (v != null) {

                        Serializer serializer = pickSerializer(argument);
                        if (serializer != null) {

                            try {
                                kafkaHeaders.add(
                                        new RecordHeader(
                                                name,
                                                serializer.serialize(
                                                        null,
                                                        v
                                                )
                                        )
                                );
                            } catch (Exception e) {
                                throw new MessagingClientException(
                                        "Cannot serialize header argument [" + argument + "] for method [" + context + "]: " + e.getMessage(), e
                                );
                            }
                        }
                    }
                }
            }


            ReturnType<Object> returnType = context.getReturnType();
            Class javaReturnType = returnType.getType();


            Object key = keyArgument != null ? parameterValues.get(keyArgument.getName()) : null;
            Object value = parameterValues.get(bodyArgument.getName());

            if (value == null) {
                throw new MessagingClientException("Value cannot be null for method: " + context);
            }

            ProducerRecord record = new ProducerRecord(
                    topic,
                    null,
                    client.timestamp() ? System.currentTimeMillis() : null,
                    key,
                    value,
                    kafkaHeaders.isEmpty() ? null : kafkaHeaders
            );

            if (Publishers.isConvertibleToPublisher(javaReturnType)) {
                Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
                Flowable returnFlowable = Flowable.create(emitter -> kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        emitter.onError(new MessagingClientException(
                                "Exception sending producer record for method [" + context + "]: " + exception.getMessage(), exception
                        ));
                    } else {
                        if (firstTypeVariable.isPresent()) {
                            Argument<?> argument = firstTypeVariable.get();
                            Optional<?> converted = conversionService.convert(metadata, argument);

                            if (converted.isPresent()) {
                                emitter.onNext(converted.get());
                            } else if (argument.getType() == bodyArgument.getType()) {
                                emitter.onNext(value);
                            }
                        }
                        emitter.onComplete();
                    }
                }), BackpressureStrategy.ERROR);
                return Publishers.convertPublisher(returnFlowable, javaReturnType);
            } else if (Future.class.isAssignableFrom(javaReturnType)) {
                if (CompletableFuture.class.isAssignableFrom(javaReturnType)) {
                    Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
                    CompletableFuture completableFuture = new CompletableFuture();

                    kafkaProducer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            completableFuture.completeExceptionally(new MessagingClientException(
                                    "Exception sending producer record for method [" + context + "]: " + exception.getMessage(), exception
                            ));
                        } else {
                            if (firstTypeVariable.isPresent()) {
                                Argument<?> argument = firstTypeVariable.get();
                                Optional<?> converted = conversionService.convert(metadata, argument);
                                if (converted.isPresent()) {
                                    completableFuture.complete(converted.get());
                                } else if (argument.getType() == bodyArgument.getType()) {
                                    completableFuture.complete(value);
                                }
                            } else {
                                completableFuture.complete(null);
                            }
                        }
                    });

                    return completableFuture;
                } else {
                    return kafkaProducer.send(record);
                }
            } else {
                // synchronous case
                Duration sendTimeout = context.getValue(KafkaClient.class, "sendTimeout", Duration.class)
                        .orElse(Duration.ofSeconds(10));

                try {
                    Object result = kafkaProducer.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

                    return conversionService.convert(result, returnType.asArgument()).orElse(null);
                } catch (Exception e) {
                    throw new MessagingClientException(
                            "Exception sending producer record for method [" + context + "]: " + e.getMessage(), e
                    );
                }
            }

        } else {
            // can't be implemented so proceed
            return context.proceed();
        }
    }

    @SuppressWarnings("unchecked")
    private KafkaProducer getProducer(Argument bodyArgument, @Nullable Argument keyArgument, String clientId) {
        Class keyType = keyArgument != null ? keyArgument.getType() : byte[].class;
        ProducerKey key = new ProducerKey(keyType, bodyArgument.getType(), clientId);
        return producerMap.computeIfAbsent(key, producerKey -> {
            String producerId = producerKey.id;
            AbstractKafkaProducerConfiguration configuration;
            if (producerId != null) {
                Optional<KafkaProducerConfiguration> namedConfig = beanContext.findBean(KafkaProducerConfiguration.class, Qualifiers.byName(producerId));
                if (namedConfig.isPresent()) {
                    configuration = namedConfig.get();
                } else {
                    configuration = beanContext.getBean(DefaultKafkaProducerConfiguration.class);
                }
            } else {
                configuration = beanContext.getBean(DefaultKafkaProducerConfiguration.class);
            }

            DefaultKafkaProducerConfiguration<?, ?> newConfiguration = new DefaultKafkaProducerConfiguration<>(
                    configuration
            );


            Serializer<?> keySerializer = newConfiguration.getKeySerializer().orElse(null);
            if (keySerializer == null) {
                if (keyArgument != null) {
                    keySerializer = pickSerializer(keyArgument);
                } else {
                    keySerializer = new ByteArraySerializer();
                }
                newConfiguration.setKeySerializer((Serializer) keySerializer);
            }

            Serializer<?> valueSerializer = newConfiguration.getValueSerializer().orElse(null);

            if (valueSerializer == null) {
                valueSerializer = pickSerializer(bodyArgument);
                newConfiguration.setValueSerializer((Serializer) valueSerializer);
            }

            return producerFactory.createProducer(newConfiguration);
        });
    }

    @Override
    @PreDestroy
    public final void close() {
        Collection<KafkaProducer> kafkaProducers = producerMap.values();
        try {
            for (KafkaProducer kafkaProducer : kafkaProducers) {
                try {
                    kafkaProducer.close();
                } catch (Exception e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Error closing Kafka producer: " + e.getMessage(), e);
                    }
                }
            }
        } finally {
            producerMap.clear();
        }
    }

    private Argument findBodyArgument(ExecutableMethod<?, ?> method) {
        return Arrays.stream(method.getArguments())
                .filter(arg -> arg.getType() == ProducerRecord.class || arg.getAnnotationMetadata().hasAnnotation(Body.class))
                .findFirst()
                .orElseGet(() ->
                        Arrays.stream(method.getArguments())
                                .filter(arg -> !arg.getAnnotationMetadata().hasStereotype(Bindable.class))
                                .findFirst()
                                .orElse(null)
                );
    }

    private Serializer<?> pickSerializer(Argument<?> argument) {
        Class<?> type = argument.getType();
        Serializer<?> deserializer;

        if (ClassUtils.isJavaLangType(type) || byte[].class == type) {
            Class wrapperType = ReflectionUtils.getWrapperType(type);
            deserializer = SerdeRegistry.DEFAULT_SERIALIZERS.get(wrapperType);
        } else {
            deserializer = serdeRegistry.getSerde(argument.getType()).serializer();
        }

        if (deserializer == null) {
            throw new SerializationException("No Kafka serializer found for argument: " + argument);
        }

        return deserializer;
    }


    /**
     * Key used to cache {@link org.apache.kafka.clients.producer.KafkaProducer} instances.
     */
    private final class ProducerKey {
        final Class keyType;
        final Class valueType;
        final String id;

        ProducerKey(Class keyType, Class valueType, String id) {
            this.keyType = keyType;
            this.valueType = valueType;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProducerKey that = (ProducerKey) o;
            return Objects.equals(keyType, that.keyType) &&
                    Objects.equals(valueType, that.valueType) &&
                    Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyType, valueType, id);
        }
    }
}
