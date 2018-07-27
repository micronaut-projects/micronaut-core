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
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.exceptions.MessagingClientException;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
            AnnotationValue<KafkaClient> client = context.findAnnotation(KafkaClient.class).orElseThrow(() -> new IllegalStateException("No @KafkaClient annotation present on method: " + context));

            boolean isBatchSend = client.getRequiredValue("batch", Boolean.class);
            String topic = context.getValue(Topic.class, String.class).orElse(null);

            if (StringUtils.isEmpty(topic)) {
                throw new MessagingClientException("No topic specified for method: " + context);
            }
            Argument bodyArgument = findBodyArgument(context);
            if (bodyArgument == null) {
                throw new MessagingClientException("No valid message body argument found for method: " + context);
            }

            Argument keyArgument = Arrays.stream(context.getArguments())
                    .filter(arg -> arg.isAnnotationPresent(KafkaKey.class))
                    .findFirst().orElse(null);

            KafkaProducer kafkaProducer = getProducer(bodyArgument, keyArgument, context);

            List<Header> kafkaHeaders = new ArrayList<>();
            List<AnnotationValue<io.micronaut.messaging.annotation.Header>> headers = context.getAnnotationValuesByType(io.micronaut.messaging.annotation.Header.class);

            for (AnnotationValue<io.micronaut.messaging.annotation.Header> header : headers) {
                String name = header.get("name", String.class).orElse(null);
                String value = header.getValue(String.class).orElse(null);

                if (StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)) {
                    kafkaHeaders.add(
                            new RecordHeader(
                                    name,
                                    value.getBytes(StandardCharsets.UTF_8)
                            )
                    );
                }
            }

            Argument[] arguments = context.getArguments();
            Map<String, Object> parameterValues = context.getParameterValueMap();

            for (Argument argument : arguments) {
                AnnotationValue<io.micronaut.messaging.annotation.Header> headerAnn = argument.getAnnotation(io.micronaut.messaging.annotation.Header.class);
                if (headerAnn != null) {
                    String argumentName = argument.getName();
                    String name = headerAnn.get("name", String.class).orElse(headerAnn.getValue(String.class).orElse(argumentName));
                    Object v = parameterValues.get(argumentName);

                    if (v != null) {

                        Serializer serializer = serdeRegistry.pickSerializer(argument);
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
            boolean isReactiveReturnType = Publishers.isConvertibleToPublisher(javaReturnType);
            Duration maxBlock = context.getValue(KafkaClient.class, "maxBlock", Duration.class)
                    .orElse(null);

            if (value == null) {
                throw new MessagingClientException("Value cannot be null for method: " + context);
            }

            boolean isReactiveValue = Publishers.isConvertibleToPublisher(value.getClass());


            if (isReactiveReturnType) {
                Flowable returnFlowable;
                if (isReactiveValue) {
                    Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
                    returnFlowable = buildSendFlowable(
                            context,
                            client,
                            topic,
                            kafkaProducer,
                            kafkaHeaders,
                            firstTypeVariable.orElse(Argument.OBJECT_ARGUMENT),
                            key,
                            value,
                            maxBlock);

                } else {
                    if (isBatchSend) {
                        Object batchValue;
                        if (value.getClass().isArray()) {
                            batchValue = Arrays.asList((Object[]) value);
                        } else {
                            batchValue = value;
                        }

                        Flowable<Object> bodyEmitter;
                        if (batchValue instanceof Iterable) {
                            bodyEmitter = Flowable.fromIterable((Iterable) batchValue);
                        } else {
                            bodyEmitter = Flowable.just(batchValue);
                        }

                        returnFlowable = bodyEmitter.flatMap(o ->
                                buildSendFlowable(context, client, topic, bodyArgument, kafkaProducer, kafkaHeaders, returnType, key, o)
                        );

                    } else {
                        returnFlowable = buildSendFlowable(context, client, topic, bodyArgument, kafkaProducer, kafkaHeaders, returnType, key, value);
                    }
                }
                return Publishers.convertPublisher(returnFlowable, javaReturnType);
            } else if (Future.class.isAssignableFrom(javaReturnType)) {
                Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
                CompletableFuture completableFuture = new CompletableFuture();

                if (isReactiveValue) {
                    Flowable sendFlowable = buildSendFlowable(
                            context,
                            client,
                            topic,
                            kafkaProducer,
                            kafkaHeaders,
                            firstTypeVariable.orElse(Argument.of(RecordMetadata.class)),
                            key,
                            value,
                            maxBlock);

                    if (!Publishers.isSingle(value.getClass())) {
                        sendFlowable = sendFlowable.toList().toFlowable();
                    }

                    //noinspection SubscriberImplementation
                    sendFlowable.subscribe(new Subscriber() {
                        boolean completed = false;
                        @Override
                        public void onSubscribe(Subscription s) {
                            s.request(1);
                        }

                        @Override
                        public void onNext(Object o) {
                            completableFuture.complete(o);
                            completed = true;
                        }

                        @Override
                        public void onError(Throwable t) {
                            completableFuture.completeExceptionally(wrapException(context, t));
                        }

                        @Override
                        public void onComplete() {
                            if (!completed) {
                                // empty publisher
                                completableFuture.complete(null);
                            }
                        }
                    });
                } else {

                    ProducerRecord record = buildProducerRecord(client, topic, kafkaHeaders, key, value);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("@KafkaClient method [" + context + "] Sending producer record: " + record);
                    }

                    kafkaProducer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            completableFuture.completeExceptionally(wrapException(context, exception));
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
                }

                return completableFuture;
            } else {

                Argument<Object> returnTypeArgument = returnType.asArgument();
                if (isReactiveValue) {
                    Flowable<Object> sendFlowable = buildSendFlowable(
                            context,
                            client,
                            topic,
                            kafkaProducer,
                            kafkaHeaders,
                            returnTypeArgument,
                            key,
                            value,
                            maxBlock
                    );

                    if (Iterable.class.isAssignableFrom(javaReturnType)) {
                        return conversionService
                                .convert(sendFlowable.toList().blockingGet(), returnTypeArgument).orElse(null);
                    } else if (void.class.isAssignableFrom(javaReturnType)) {
                        // a maybe will return null, and not throw an exception
                        Maybe<Object> maybe = sendFlowable.firstElement();
                        return maybe.blockingGet();
                    } else {
                        return conversionService
                                .convert(sendFlowable.blockingFirst(), returnTypeArgument).orElse(null);
                    }
                } else {
                    try {
                        if (isBatchSend) {
                            Iterable batchValue;
                            if (value.getClass().isArray()) {
                                batchValue = Arrays.asList((Object[]) value);
                            } else if (!(value instanceof Iterable)) {
                                batchValue = Collections.singletonList(value);
                            } else {
                                batchValue = (Iterable) value;
                            }

                            List results = new ArrayList();
                            for (Object o : batchValue) {
                                ProducerRecord record = buildProducerRecord(client, topic, kafkaHeaders, key, o);

                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("@KafkaClient method [" + context + "] Sending producer record: " + record);
                                }

                                Object result;
                                if (maxBlock != null) {
                                    result = kafkaProducer.send(record).get(maxBlock.toMillis(), TimeUnit.MILLISECONDS);
                                } else {
                                    result = kafkaProducer.send(record).get();
                                }
                                results.add(result);
                            }
                            return conversionService.convert(results, returnTypeArgument).orElseGet(() -> {
                                if (javaReturnType == bodyArgument.getType()) {
                                    return value;
                                } else {
                                    return null;
                                }
                            });
                        }
                        ProducerRecord record = buildProducerRecord(client, topic, kafkaHeaders, key, value);

                        if (LOG.isTraceEnabled()) {
                            LOG.trace("@KafkaClient method [" + context + "] Sending producer record: " + record);
                        }

                        Object result;
                        if (maxBlock != null) {
                            result = kafkaProducer.send(record).get(maxBlock.toMillis(), TimeUnit.MILLISECONDS);
                        } else {
                            result = kafkaProducer.send(record).get();
                        }

                        return conversionService.convert(result, returnTypeArgument).orElseGet(() -> {
                            if (javaReturnType == bodyArgument.getType()) {
                                return value;
                            } else {
                                return null;
                            }
                        });
                    } catch (Exception e) {
                        throw wrapException(context, e);
                    }
                }
            }

        } else {
            // can't be implemented so proceed
            return context.proceed();
        }
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

    private Flowable buildSendFlowable(
            MethodInvocationContext<Object, Object> context,
            AnnotationValue<KafkaClient> client,
            String topic,
            Argument bodyArgument,
            KafkaProducer kafkaProducer,
            List<Header> kafkaHeaders,
            ReturnType<Object> returnType, Object key, Object value) {
        Flowable returnFlowable;
        ProducerRecord record = buildProducerRecord(client, topic, kafkaHeaders, key, value);
        Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
        returnFlowable = Flowable.create(emitter -> kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                emitter.onError(wrapException(context, exception));
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
        return returnFlowable;
    }

    private Flowable<Object> buildSendFlowable(
            MethodInvocationContext<Object, Object> context,
            AnnotationValue<KafkaClient> client,
            String topic,
            KafkaProducer kafkaProducer,
            List<Header> kafkaHeaders,
            Argument<?> returnType,
            Object key,
            Object value,
            Duration maxBlock) {
        Flowable<?> valueFlowable = Publishers.convertPublisher(value, Flowable.class);
        Class<?> javaReturnType = returnType.getType();

        if (Iterable.class.isAssignableFrom(javaReturnType)) {
            javaReturnType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT).getType();
        }

        Class<?> finalJavaReturnType = javaReturnType;
        Flowable<Object> sendFlowable = valueFlowable.flatMap(o -> {
            ProducerRecord record = buildProducerRecord(client, topic, kafkaHeaders, key, o);

            if (LOG.isTraceEnabled()) {
                LOG.trace("@KafkaClient method [" + context + "] Sending producer record: " + record);
            }

            //noinspection unchecked
            return Flowable.create(emitter -> kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    emitter.onError(wrapException(context, exception));
                } else {
                    if (RecordMetadata.class.isAssignableFrom(finalJavaReturnType)) {
                        emitter.onNext(metadata);
                    } else if (finalJavaReturnType.isInstance(o)) {
                        emitter.onNext(o);
                    } else {
                        Optional converted = conversionService.convert(metadata, finalJavaReturnType);
                        if (converted.isPresent()) {
                            emitter.onNext(converted.get());
                        }
                    }

                    emitter.onComplete();
                }
            }), BackpressureStrategy.BUFFER);
        });

        if (maxBlock != null) {
            sendFlowable = sendFlowable.timeout(maxBlock.toMillis(), TimeUnit.MILLISECONDS);
        }
        return sendFlowable;
    }

    private MessagingClientException wrapException(MethodInvocationContext<Object, Object> context, Throwable exception) {
        return new MessagingClientException(
                "Exception sending producer record for method [" + context + "]: " + exception.getMessage(), exception
        );
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord buildProducerRecord(AnnotationValue<KafkaClient> client, String topic, List<Header> kafkaHeaders, Object key, Object value) {
        return new ProducerRecord(
                        topic,
                        null,
                        client.getRequiredValue("timestamp", Boolean.class) ? System.currentTimeMillis() : null,
                        key,
                        value,
                        kafkaHeaders.isEmpty() ? null : kafkaHeaders
                );
    }

    @SuppressWarnings("unchecked")
    private KafkaProducer getProducer(Argument bodyArgument, @Nullable Argument keyArgument, AnnotationMetadata metadata) {
        Class keyType = keyArgument != null ? keyArgument.getType() : byte[].class;
        String clientId = metadata.getValue(KafkaClient.class, String.class).orElse(null);
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

            Properties newProperties = newConfiguration.getConfig();

            if (clientId != null) {
                newProperties.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, clientId);
            }

            metadata.getValue(KafkaClient.class, "maxBlock", Duration.class).ifPresent(maxBlock ->
                    newProperties.put(
                            ProducerConfig.MAX_BLOCK_MS_CONFIG,
                            String.valueOf(maxBlock.toMillis())
                    ));

            Integer ack = metadata.getValue(KafkaClient.class, "acks", Integer.class).orElse(KafkaClient.Acknowledge.DEFAULT);

            if (ack != KafkaClient.Acknowledge.DEFAULT) {
                String acksValue = ack == -1 ? "all" : String.valueOf(ack);
                newProperties.put(
                        ProducerConfig.ACKS_CONFIG,
                        acksValue
                );
            }

            List<AnnotationValue<Property>> additionalProperties = metadata.findAnnotation(KafkaClient.class).map(ann ->
                    ann.getAnnotations("properties", Property.class)
            ).orElse(Collections.emptyList());

            for (AnnotationValue<Property> additionalProperty : additionalProperties) {
                String v = additionalProperty.getValue(String.class).orElse(null);
                String n = additionalProperty.get("name", String.class).orElse(null);

                if (StringUtils.isNotEmpty(n) && StringUtils.isNotEmpty(v)) {
                    newProperties.put(n, v);
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating new KafkaProducer.");
            }

            if (!newProperties.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)) {
                Serializer<?> keySerializer = newConfiguration.getKeySerializer().orElse(null);
                if (keySerializer == null) {
                    if (keyArgument != null) {
                        keySerializer = serdeRegistry.pickSerializer(keyArgument);
                    } else {
                        keySerializer = new ByteArraySerializer();
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using Kafka key serializer: {}", keySerializer);
                    }
                    newConfiguration.setKeySerializer((Serializer) keySerializer);
                }
            }

            if (!newProperties.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                Serializer<?> valueSerializer = newConfiguration.getValueSerializer().orElse(null);

                if (valueSerializer == null) {
                    boolean batch = metadata.getValue(KafkaClient.class, "batch", Boolean.class).orElse(false);
                    valueSerializer = serdeRegistry.pickSerializer(batch ? bodyArgument.getFirstTypeVariable().orElse(bodyArgument) : bodyArgument);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using Kafka value serializer: {}", valueSerializer);
                    }
                    newConfiguration.setValueSerializer((Serializer) valueSerializer);
                }
            }

            return producerFactory.createProducer(newConfiguration);
        });
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
