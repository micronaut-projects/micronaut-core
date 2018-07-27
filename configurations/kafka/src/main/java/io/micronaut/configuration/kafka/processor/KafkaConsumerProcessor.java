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

package io.micronaut.configuration.kafka.processor;

import io.micronaut.configuration.kafka.Acknowledgement;
import io.micronaut.configuration.kafka.KafkaConsumerAware;
import io.micronaut.configuration.kafka.KafkaProducerRegistry;
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.configuration.kafka.bind.ConsumerRecordBinderRegistry;
import io.micronaut.configuration.kafka.bind.batch.BatchConsumerRecordsBinderRegistry;
import io.micronaut.configuration.kafka.config.AbstractKafkaConsumerConfiguration;
import io.micronaut.configuration.kafka.config.DefaultKafkaConsumerConfiguration;
import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerException;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerExceptionHandler;
import io.micronaut.configuration.kafka.serde.SerdeRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.*;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.annotation.SendTo;
import io.micronaut.messaging.exceptions.MessagingSystemException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;


/**
 * <p>A {@link ExecutableMethodProcessor} that will process all beans annotated with {@link KafkaListener}
 * and create and subscribe the relevant methods as consumers to Kafka topics.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(beans = KafkaDefaultConfiguration.class)
public class KafkaConsumerProcessor implements ExecutableMethodProcessor<KafkaListener>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerProcessor.class);

    private final ExecutorService executorService;
    private final ApplicationConfiguration applicationConfiguration;
    private final BeanContext beanContext;
    private final AbstractKafkaConsumerConfiguration defaultConsumerConfiguration;
    private final Queue<Consumer> consumers = new ConcurrentLinkedDeque<>();
    private final ConsumerRecordBinderRegistry binderRegistry;
    private final SerdeRegistry serdeRegistry;
    private final Scheduler executorScheduler;
    private final KafkaListenerExceptionHandler exceptionHandler;
    private final KafkaProducerRegistry producerRegistry;
    private final BatchConsumerRecordsBinderRegistry batchBinderRegistry;

    /**
     * Creates a new processor using the given {@link ExecutorService} to schedule consumers on.
     *
     * @param executorService              The executor service
     * @param applicationConfiguration     The application configuration
     * @param beanContext                  The bean context
     * @param defaultConsumerConfiguration The default consumer config
     * @param binderRegistry               The {@link ConsumerRecordBinderRegistry}
     * @param batchBinderRegistry          The {@link BatchConsumerRecordsBinderRegistry}
     * @param serdeRegistry                The {@link org.apache.kafka.common.serialization.Serde} registry
     * @param producerRegistry             The {@link KafkaProducerRegistry}
     * @param exceptionHandler             The exception handler to use
     */
    public KafkaConsumerProcessor(
            @Named(TaskExecutors.MESSAGE_CONSUMER) ExecutorService executorService,
            ApplicationConfiguration applicationConfiguration,
            BeanContext beanContext,
            AbstractKafkaConsumerConfiguration defaultConsumerConfiguration,
            ConsumerRecordBinderRegistry binderRegistry,
            BatchConsumerRecordsBinderRegistry batchBinderRegistry,
            SerdeRegistry serdeRegistry,
            KafkaProducerRegistry producerRegistry,
            KafkaListenerExceptionHandler exceptionHandler) {
        this.executorService = executorService;
        this.applicationConfiguration = applicationConfiguration;
        this.beanContext = beanContext;
        this.defaultConsumerConfiguration = defaultConsumerConfiguration;
        this.binderRegistry = binderRegistry;
        this.batchBinderRegistry = batchBinderRegistry;
        this.serdeRegistry = serdeRegistry;
        this.executorScheduler = Schedulers.from(executorService);
        this.producerRegistry = producerRegistry;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {

        Topic[] topicAnnotations = method.synthesizeDeclaredAnnotationsByType(Topic.class);
        AnnotationValue<KafkaListener> consumerAnnotation = method.getAnnotation(KafkaListener.class);

        if (ArrayUtils.isEmpty(topicAnnotations)) {
            topicAnnotations = beanDefinition.synthesizeAnnotationsByType(Topic.class);
        }

        if (consumerAnnotation != null && ArrayUtils.isNotEmpty(topicAnnotations)) {

            Duration pollTimeout = method.getValue(KafkaListener.class, "pollTimeout", Duration.class)
                    .orElse(Duration.ofMillis(100));

            Duration sessionTimeout = method.getValue(KafkaListener.class, "sessionTimeout", Duration.class)
                    .orElse(null);

            Duration heartbeatInterval = method.getValue(KafkaListener.class, "heartbeatInterval", Duration.class)
                    .orElse(null);

            boolean isBatch = method.getValue(KafkaListener.class, "batch", Boolean.class)
                    .orElse(false);

            Optional<Argument> consumerArg = Arrays.stream(method.getArguments()).filter(arg -> Consumer.class.isAssignableFrom(arg.getType())).findFirst();
            Optional<Argument> ackArg = Arrays.stream(method.getArguments()).filter(arg -> Acknowledgement.class.isAssignableFrom(arg.getType())).findFirst();

            String groupId = consumerAnnotation.get("groupId", String.class).orElse(null);

            if (StringUtils.isEmpty(groupId)) {
                groupId = applicationConfiguration.getName().orElse(beanDefinition.getBeanType().getName());
            }

            String clientId = consumerAnnotation.get("clientId", String.class).orElse(null);

            if (StringUtils.isEmpty(clientId)) {
                clientId = applicationConfiguration.getName().orElse(null);
            }

            OffsetStrategy offsetStrategy = consumerAnnotation.getRequiredValue("offsetStrategy", OffsetStrategy.class);
            int consumerThreads = consumerAnnotation.getRequiredValue("threads", Integer.class);

            AbstractKafkaConsumerConfiguration consumerConfigurationDefaults = beanContext.findBean(AbstractKafkaConsumerConfiguration.class, Qualifiers.byName(groupId))
                    .orElse(defaultConsumerConfiguration);


            DefaultKafkaConsumerConfiguration consumerConfiguration = new DefaultKafkaConsumerConfiguration<>(consumerConfigurationDefaults);

            Properties properties = consumerConfiguration.getConfig();

            if (consumerAnnotation.getRequiredValue("offsetReset", OffsetReset.class) == OffsetReset.EARLIEST) {
                properties.putIfAbsent(
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        OffsetReset.EARLIEST.name().toLowerCase()
                );

            }

            // enable auto commit offsets if necessary
            properties.putIfAbsent(
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                    String.valueOf(offsetStrategy == OffsetStrategy.AUTO)
            );

            if (heartbeatInterval != null) {
                properties.putIfAbsent(
                        ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
                        String.valueOf(heartbeatInterval.toMillis())
                );
            }

            if (sessionTimeout != null) {
                long sessionTimeoutMillis = sessionTimeout.toMillis();
                properties.putIfAbsent(
                        ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                        String.valueOf(sessionTimeoutMillis)
                );
            }

            properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

            if (clientId != null) {
                properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            }

            List<AnnotationValue<Property>> additionalProperties = consumerAnnotation.getAnnotations("properties", Property.class);

            if (!additionalProperties.isEmpty()) {
                for (AnnotationValue<Property> property : additionalProperties) {
                    String v = property.getValue(String.class).orElse(null);
                    String n = property.get("name", String.class).orElse(null);
                    if (StringUtils.isNotEmpty(n) && StringUtils.isNotEmpty(v)) {
                        properties.put(n, v);
                    }
                }
            }

            configureDeserializers(method, consumerConfiguration);

            if (LOG.isDebugEnabled()) {
                Optional kd = consumerConfiguration.getKeyDeserializer();
                if (kd.isPresent()) {
                    LOG.debug("Using key deserializer [{}] for Kafka listener: {}", kd.get(), method);
                } else {
                    LOG.debug("Using key deserializer [{}] for Kafka listener: {}", properties.getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG), method);
                }
                Optional vd = consumerConfiguration.getValueDeserializer();

                if (vd.isPresent()) {
                    LOG.debug("Using value deserializer [{}] for Kafka listener: {}", vd.get(), method);
                } else {
                    LOG.debug("Using value deserializer [{}] for Kafka listener: {}", properties.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG), method);
                }
            }


            for (int i = 0; i < consumerThreads; i++) {
                KafkaConsumer kafkaConsumer = beanContext.createBean(KafkaConsumer.class, consumerConfiguration);
                Object consumerBean = beanContext.getBean(beanDefinition.getBeanType());

                if (consumerBean instanceof KafkaConsumerAware) {
                    //noinspection unchecked
                    ((KafkaConsumerAware) consumerBean).setKafkaConsumer(kafkaConsumer);
                }

                consumers.add(kafkaConsumer);

                for (Topic topicAnnotation : topicAnnotations) {
                    String[] topicNames = topicAnnotation.value();
                    String[] patterns = topicAnnotation.patterns();
                    boolean hasTopics = ArrayUtils.isNotEmpty(topicNames);
                    boolean hasPatterns = ArrayUtils.isNotEmpty(patterns);

                    if (!hasTopics && !hasPatterns) {
                        throw new MessagingSystemException("Either a topic or a topic must be specified for method: " + method);
                    }

                    if (hasTopics) {
                        List<String> topics = Arrays.asList(topicNames);
                        if (consumerBean instanceof ConsumerRebalanceListener) {
                            kafkaConsumer.subscribe(topics, (ConsumerRebalanceListener) consumerBean);
                        } else {
                            kafkaConsumer.subscribe(topics);
                        }

                        if (LOG.isInfoEnabled()) {
                            LOG.info("Kafka listener [{}] subscribed to topics: {}", method, topics);
                        }
                    }


                    if (hasPatterns) {
                        for (String pattern : patterns) {
                            Pattern p;
                            try {
                                p = Pattern.compile(pattern);
                            } catch (Exception e) {
                                throw new MessagingSystemException("Invalid topic pattern [" + pattern + "] for method [" + method + "]: " + e.getMessage(), e);
                            }

                            if (consumerBean instanceof ConsumerRebalanceListener) {
                                kafkaConsumer.subscribe(p, (ConsumerRebalanceListener) consumerBean);
                            } else {
                                kafkaConsumer.subscribe(p);
                            }

                            if (LOG.isInfoEnabled()) {
                                LOG.info("Kafka listener [{}] subscribed to topics pattern: {}", method, pattern);
                            }
                        }
                    }
                }
                executorService.submit(() -> {
                    try {

                        boolean trackPartitions = ackArg.isPresent() || offsetStrategy == OffsetStrategy.SYNC_PER_RECORD || offsetStrategy == OffsetStrategy.ASYNC_PER_RECORD;
                        Map<Argument<?>, Object> boundArguments = new HashMap<>(2);
                        consumerArg.ifPresent(argument -> boundArguments.put(argument, kafkaConsumer));

                        //noinspection InfiniteLoopStatement
                        while (true) {
                            ConsumerRecords<?, ?> consumerRecords = kafkaConsumer.poll(pollTimeout.toMillis());
                            Map<TopicPartition, OffsetAndMetadata> currentOffsets = trackPartitions ? new HashMap<>() : null;

                            try {
                                if (consumerRecords != null && consumerRecords.count() > 0) {

                                    if (isBatch) {

                                        ExecutableBinder<ConsumerRecords<?, ?>> batchBinder = new DefaultExecutableBinder<>(boundArguments);
                                        BoundExecutable boundExecutable = batchBinder.bind(method, batchBinderRegistry, consumerRecords);
                                        Object result = boundExecutable.invoke(consumerBean);

                                        // handle batch result
                                        if (result != null) {
                                            if (result.getClass().isArray()) {
                                                result = Arrays.asList((Object[]) result);
                                            }

                                            boolean isPublisher = Publishers.isConvertibleToPublisher(result);
                                            Flowable<?> resultFlowable;
                                            if (result instanceof Iterable) {
                                                resultFlowable = Flowable.fromIterable((Iterable) result);
                                            } else {
                                                if (isPublisher) {
                                                    resultFlowable = Publishers.convertPublisher(result, Flowable.class);
                                                } else {
                                                    resultFlowable = Flowable.just(result);
                                                }
                                            }

                                            Iterator<? extends ConsumerRecord<?, ?>> iterator = consumerRecords.iterator();
                                            boolean isBlocking = !isPublisher || method.hasAnnotation(Blocking.class);

                                            if (isBlocking) {
                                                resultFlowable.blockingSubscribe(o -> {
                                                    if (iterator.hasNext()) {
                                                        ConsumerRecord<?, ?> consumerRecord = iterator.next();

                                                        handleResultFlowable(
                                                                consumerAnnotation,
                                                                consumerBean,
                                                                method,
                                                                kafkaConsumer,
                                                                consumerRecord,
                                                                Flowable.just(o),
                                                                isBlocking
                                                        );
                                                    }
                                                });
                                            } else {
                                                resultFlowable.forEach(o -> {
                                                    if (iterator.hasNext()) {
                                                        ConsumerRecord<?, ?> consumerRecord = iterator.next();

                                                        handleResultFlowable(
                                                                consumerAnnotation,
                                                                consumerBean,
                                                                method,
                                                                kafkaConsumer,
                                                                consumerRecord,
                                                                Flowable.just(o),
                                                                isBlocking
                                                        );
                                                    }
                                                });
                                            }

                                        }

                                    } else {
                                        ExecutableBinder<ConsumerRecord<?, ?>> executableBinder = new DefaultExecutableBinder<>(boundArguments);
                                        for (ConsumerRecord<?, ?> consumerRecord : consumerRecords) {

                                            if (LOG.isTraceEnabled()) {
                                                LOG.trace("Kafka consumer [{}] received record: {}", method, consumerRecord);
                                            }

                                            if (trackPartitions) {
                                                currentOffsets.put(new TopicPartition(
                                                                consumerRecord.topic(),
                                                                consumerRecord.partition()),
                                                        new OffsetAndMetadata(consumerRecord.offset() + 1, null)
                                                );
                                            }

                                            if (ackArg.isPresent()) {
                                                boundArguments.put(ackArg.get(), (Acknowledgement) () -> kafkaConsumer.commitSync(
                                                        currentOffsets
                                                ));
                                            }

                                            try {
                                                BoundExecutable boundExecutable = executableBinder.bind(method, binderRegistry, consumerRecord);
                                                Object result = boundExecutable.invoke(
                                                        consumerBean
                                                );

                                                if (result != null) {
                                                    Flowable<?> resultFlowable;
                                                    boolean isBlocking;
                                                    if (Publishers.isConvertibleToPublisher(result)) {
                                                        resultFlowable = Publishers.convertPublisher(result, Flowable.class);
                                                        isBlocking = method.hasAnnotation(Blocking.class);
                                                    } else {
                                                        resultFlowable = Flowable.just(result);
                                                        isBlocking = true;
                                                    }

                                                    handleResultFlowable(
                                                            consumerAnnotation,
                                                            consumerBean,
                                                            method,
                                                            kafkaConsumer,
                                                            consumerRecord,
                                                            resultFlowable,
                                                            isBlocking
                                                    );
                                                }
                                            } catch (Throwable e) {
                                                handleException(kafkaConsumer, consumerBean, consumerRecord, e);
                                                continue;
                                            }

                                            if (offsetStrategy == OffsetStrategy.SYNC_PER_RECORD) {
                                                try {
                                                    kafkaConsumer.commitSync(
                                                            currentOffsets
                                                    );
                                                } catch (CommitFailedException e) {
                                                    handleException(kafkaConsumer, consumerBean, consumerRecord, e);
                                                }
                                            } else if (offsetStrategy == OffsetStrategy.ASYNC_PER_RECORD) {
                                                kafkaConsumer.commitAsync(currentOffsets, resolveCommitCallback(consumerBean));
                                            }
                                        }
                                    }

                                }

                                if (offsetStrategy == OffsetStrategy.SYNC) {
                                    try {
                                        kafkaConsumer.commitSync();
                                    } catch (CommitFailedException e) {
                                        handleException(kafkaConsumer, consumerBean, null, e);
                                    }
                                } else if (offsetStrategy == OffsetStrategy.ASYNC) {
                                    kafkaConsumer.commitAsync(resolveCommitCallback(consumerBean));
                                }
                            } catch (WakeupException e) {
                                throw e;
                            } catch (Throwable e) {
                                handleException(kafkaConsumer, consumerBean, null, e);
                            }

                        }
                    } catch (WakeupException e) {
                        // ignore for shutdown
                    } finally {
                        try {
                            kafkaConsumer.commitSync();
                        } catch (Throwable e) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Error committing Kafka offsets on shutdown: " + e.getMessage(), e);
                            }
                        } finally {
                            kafkaConsumer.close();
                        }
                    }
                });
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        for (Consumer consumer : consumers) {
            consumer.wakeup();
        }
        consumers.clear();
    }

    private void handleException(KafkaConsumer kafkaConsumer, Object consumerBean, ConsumerRecord<?, ?> consumerRecord, Throwable e) {
        KafkaListenerException kafkaListenerException = new KafkaListenerException(
                e,
                consumerBean,
                kafkaConsumer,
                consumerRecord

        );
        handleException(consumerBean, kafkaListenerException);
    }

    private void handleException(Object consumerBean, KafkaListenerException kafkaListenerException) {
        if (consumerBean instanceof KafkaListenerExceptionHandler) {
            ((KafkaListenerExceptionHandler) consumerBean).handle(kafkaListenerException);
        } else {
            exceptionHandler.handle(kafkaListenerException);
        }
    }

    @SuppressWarnings({"SubscriberImplementation", "unchecked"})
    private void handleResultFlowable(
            AnnotationValue<KafkaListener> kafkaListener,
            Object consumerBean,
            ExecutableMethod<?, ?> method,
            KafkaConsumer kafkaConsumer,
            ConsumerRecord<?, ?> consumerRecord,
            Flowable<?> resultFlowable,
            boolean isBlocking) {
        Flowable<RecordMetadata> recordMetadataProducer = resultFlowable.subscribeOn(executorScheduler)
                .flatMap((Function<Object, Publisher<RecordMetadata>>) o -> {
                    String[] destinationTopics = method.getValue(SendTo.class, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
                    if (ArrayUtils.isNotEmpty(destinationTopics)) {
                        Object key = consumerRecord.key();
                        Object value = o;

                        if (value != null) {
                            String groupId = kafkaListener.get("groupId", String.class).orElse(null);
                            KafkaProducer kafkaProducer = producerRegistry.getProducer(
                                    StringUtils.isNotEmpty(groupId) ? groupId : null,
                                    Argument.of((Class) (key != null ? key.getClass() : byte[].class)),
                                    Argument.of(value.getClass())
                            );

                            return Flowable.create(emitter -> {
                                for (String destinationTopic : destinationTopics) {
                                    ProducerRecord record = new ProducerRecord(
                                            destinationTopic,
                                            null,
                                            key,
                                            value,
                                            consumerRecord.headers()
                                    );

                                    kafkaProducer.send(record, (metadata, exception) -> {
                                        if (exception != null) {
                                            emitter.onError(exception);
                                        } else {
                                            emitter.onNext(metadata);
                                        }
                                    });

                                }
                                emitter.onComplete();
                            }, BackpressureStrategy.ERROR);


                        }
                        return Flowable.empty();
                    }
                    return Flowable.empty();
                }).onErrorResumeNext((Function<Throwable, Publisher<RecordMetadata>>) throwable -> {
                    handleException(consumerBean, new KafkaListenerException(
                            "Error occurred processing record [" + consumerRecord + "] with Kafka reactive consumer [" + method + "]: " + throwable.getMessage(),
                            throwable,
                            consumerBean,
                            kafkaConsumer,
                            consumerRecord
                    ));

                    if (kafkaListener.getRequiredValue("redelivery", Boolean.class)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Attempting redelivery of record [{}] following error", consumerRecord);
                        }

                        Object key = consumerRecord.key();
                        Object value = consumerRecord.value();


                        if (key != null && value != null) {
                            String groupId = kafkaListener.get("groupId", String.class).orElse(null);
                            KafkaProducer kafkaProducer = producerRegistry.getProducer(
                                    StringUtils.isNotEmpty(groupId) ? groupId : null,
                                    Argument.of(key.getClass()),
                                    Argument.of(value.getClass())
                            );

                            ProducerRecord record = new ProducerRecord(
                                    consumerRecord.topic(),
                                    consumerRecord.partition(),
                                    key,
                                    value,
                                    consumerRecord.headers()
                            );

                            return Flowable.create(emitter -> kafkaProducer.send(record, (metadata, exception) -> {
                                if (exception != null) {
                                    handleException(consumerBean, new KafkaListenerException(
                                            "Redelivery failed for record [" + consumerRecord + "] with Kafka reactive consumer [" + method + "]: " + throwable.getMessage(),
                                            throwable,
                                            consumerBean,
                                            kafkaConsumer,
                                            consumerRecord
                                    ));

                                    emitter.onComplete();
                                } else {
                                    emitter.onNext(metadata);
                                    emitter.onComplete();
                                }
                            }), BackpressureStrategy.ERROR);


                        }
                    }
                    return Flowable.empty();
                });


        if (isBlocking) {
            recordMetadataProducer.blockingSubscribe(recordMetadata -> {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Method [{}] produced record metadata: {}", method, recordMetadata);
                }
            });
        } else {
            //noinspection ResultOfMethodCallIgnored
            recordMetadataProducer.subscribe(recordMetadata -> {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Method [{}] produced record metadata: {}", method, recordMetadata);
                }
            });
        }
    }

    private Argument findBodyArgument(ExecutableMethod<?, ?> method) {
        return Arrays.stream(method.getArguments())
                .filter(arg -> arg.getType() == ConsumerRecord.class || arg.getAnnotationMetadata().hasAnnotation(Body.class))
                .findFirst()
                .orElseGet(() ->
                        Arrays.stream(method.getArguments())
                                .filter(arg -> !arg.getAnnotationMetadata().hasStereotype(Bindable.class))
                                .findFirst()
                                .orElse(null)
                );
    }

    private void configureDeserializers(ExecutableMethod<?, ?> method, DefaultKafkaConsumerConfiguration consumerConfiguration) {
        Properties properties = consumerConfiguration.getConfig();
        // figure out the Key deserializer
        Argument bodyArgument = findBodyArgument(method);

        if (!properties.containsKey(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)) {
            if (!consumerConfiguration.getKeyDeserializer().isPresent()) {
                Optional<Argument> keyArgument = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.isAnnotationPresent(KafkaKey.class)).findFirst();

                if (keyArgument.isPresent()) {
                    consumerConfiguration.setKeyDeserializer(
                            serdeRegistry.pickDeserializer(keyArgument.get())
                    );
                } else {
                    //noinspection SingleStatementInBlock
                    if (bodyArgument != null && ConsumerRecord.class.isAssignableFrom(bodyArgument.getType())) {
                        Optional<Argument<?>> keyType = bodyArgument.getTypeVariable("K");
                        if (keyType.isPresent()) {
                            consumerConfiguration.setKeyDeserializer(
                                    serdeRegistry.pickDeserializer(keyType.get())
                            );
                        } else {
                            consumerConfiguration.setKeyDeserializer(new ByteArrayDeserializer());
                        }
                    } else {
                        consumerConfiguration.setKeyDeserializer(new ByteArrayDeserializer());
                    }
                }
            }
        }

        // figure out the Value deserializer
        if (!properties.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG) && !consumerConfiguration.getValueDeserializer().isPresent()) {

            if (bodyArgument != null) {

                if (ConsumerRecord.class.isAssignableFrom(bodyArgument.getType())) {
                    Optional<Argument<?>> valueType = bodyArgument.getTypeVariable("V");
                    if (valueType.isPresent()) {
                        consumerConfiguration.setValueDeserializer(
                                serdeRegistry.pickDeserializer(valueType.get())
                        );
                    } else {
                        consumerConfiguration.setValueDeserializer(new StringDeserializer());
                    }

                } else {
                    boolean batch = method.getValue(KafkaListener.class, "batch", Boolean.class).orElse(false);

                    consumerConfiguration.setValueDeserializer(
                            serdeRegistry.pickDeserializer(batch ? bodyArgument.getFirstTypeVariable().orElse(bodyArgument) : bodyArgument)
                    );
                }
            } else {
                //noinspection SingleStatementInBlock
                consumerConfiguration.setValueDeserializer(new StringDeserializer());
            }
        }
    }

    private OffsetCommitCallback resolveCommitCallback(Object consumerBean) {
        return (offsets, exception) -> {
            if (consumerBean instanceof OffsetCommitCallback) {
                ((OffsetCommitCallback) consumerBean).onComplete(offsets, exception);
            } else if (exception != null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error asynchronously committing Kafka offsets [" + offsets + "]: " + exception.getMessage(), exception);
                }
            }
        };
    }

}
