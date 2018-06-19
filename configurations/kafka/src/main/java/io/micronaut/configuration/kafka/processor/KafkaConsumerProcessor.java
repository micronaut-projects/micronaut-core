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

import io.micronaut.configuration.kafka.AbstractKafkaConsumerConfiguration;
import io.micronaut.configuration.kafka.DefaultKafkaConsumerConfiguration;
import io.micronaut.configuration.kafka.annotation.KafkaConsumer;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.configuration.kafka.bind.ConsumerRecordBinderRegistry;
import io.micronaut.configuration.kafka.serde.SerdeRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;


/**
 * <p>A {@link ExecutableMethodProcessor} that will process all beans annotated with {@link KafkaConsumer}
 * and create and subscribe the relevant methods as consumers to Kafka topics.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class KafkaConsumerProcessor implements ExecutableMethodProcessor<KafkaConsumer>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerProcessor.class);
    @SuppressWarnings({"unused", "unchecked"})
    private static final Map<Class, Deserializer> DEFAULT_DESERIALIZERS = new LinkedHashMap() {{
        put(String.class, new StringDeserializer());
        put(Integer.class, new IntegerDeserializer());
        put(Float.class, new FloatDeserializer());
        put(Short.class, new ShortDeserializer());
        put(Long.class, new LongDeserializer());
        put(Double.class, new DoubleDeserializer());
        put(byte[].class, new ByteArrayDeserializer());
    }};

    private final ExecutorService executorService;
    private final ApplicationConfiguration applicationConfiguration;
    private final BeanContext beanContext;
    private final AbstractKafkaConsumerConfiguration defaultConsumerConfiguration;
    private final Map<ExecutableMethod<?, ?>, Consumer> consumerMap = new ConcurrentHashMap<>();
    private final ConsumerRecordBinderRegistry binderRegistry;
    private final DefaultExecutableBinder<ConsumerRecord<?, ?>> executableBinder;
    private final SerdeRegistry serdeRegistry;

    /**
     * Creates a new processor using the given {@link ExecutorService} to schedule consumers on.
     *
     * @param executorService The executor service
     * @param applicationConfiguration The application configuration
     * @param beanContext The bean context
     * @param defaultConsumerConfiguration The default consumer config
     * @param binderRegistry The {@link ConsumerRecordBinderRegistry}
     * @param serdeRegistry The {@link Serde} registry
     */
    public KafkaConsumerProcessor(
            @Named(TaskExecutors.MESSAGE_CONSUMER) ExecutorService executorService,
            ApplicationConfiguration applicationConfiguration,
            BeanContext beanContext,
            AbstractKafkaConsumerConfiguration defaultConsumerConfiguration,
            ConsumerRecordBinderRegistry binderRegistry,
            SerdeRegistry serdeRegistry) {
        this.executorService = executorService;
        this.applicationConfiguration = applicationConfiguration;
        this.beanContext = beanContext;
        this.defaultConsumerConfiguration = defaultConsumerConfiguration;
        this.binderRegistry = binderRegistry;
        this.executableBinder = new DefaultExecutableBinder<>();
        this.serdeRegistry = serdeRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {

        Topic[] topicAnnotations = method.getAnnotationsByType(Topic.class);
        KafkaConsumer consumerAnnotation = method.getAnnotation(KafkaConsumer.class);

        if (consumerAnnotation != null && ArrayUtils.isNotEmpty(topicAnnotations)) {

            Object consumerBean = beanContext.getBean(beanDefinition.getBeanType());
            Duration pollTimeout = method.getValue(KafkaConsumer.class, "pollTimeout", Duration.class)
                                         .orElse(Duration.ofMillis(100));
            String groupId = consumerAnnotation.groupId();

            if (StringUtils.isEmpty(groupId)) {
                groupId = applicationConfiguration.getName().orElse(beanDefinition.getBeanType().getSimpleName());
            }

            String clientId = consumerAnnotation.clientId();

            if (StringUtils.isEmpty(clientId)) {
                clientId = applicationConfiguration.getName().orElse(null);
            }

            OffsetStrategy offsetStrategy = consumerAnnotation.offsetStrategy();
            int consumerThreads = consumerAnnotation.threads();

            AbstractKafkaConsumerConfiguration consumerConfigurationDefaults = beanContext.findBean(AbstractKafkaConsumerConfiguration.class, Qualifiers.byName(groupId))
                    .orElse(defaultConsumerConfiguration);


            DefaultKafkaConsumerConfiguration consumerConfiguration = new DefaultKafkaConsumerConfiguration<>(consumerConfigurationDefaults);

            Properties properties = consumerConfiguration.getConfig();

            // enable auto commit offsets if necessary
            properties.putIfAbsent(
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                    offsetStrategy == OffsetStrategy.AUTO
            );

            properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

            if (clientId != null) {
                properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            }

            Property[] additionalProperties = consumerAnnotation.properties();

            if (ArrayUtils.isNotEmpty(additionalProperties)) {
                for (Property property : additionalProperties) {
                    properties.put(property.name(), property.value());
                }
            }

            // figure out the Key deserializer
            if (!consumerConfiguration.getKeyDeserializer().isPresent()) {
                Optional<Argument> keyArgument = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.getAnnotation(KafkaKey.class) != null).findFirst();

                if (keyArgument.isPresent()) {
                    consumerConfiguration.setKeyDeserializer(
                            pickDeserializer(keyArgument.get())
                    );
                } else {
                    //noinspection SingleStatementInBlock
                    consumerConfiguration.setKeyDeserializer(new ByteArrayDeserializer());
                }
            }

            // figure out the Value deserializer
            if (!consumerConfiguration.getValueDeserializer().isPresent()) {
                Optional<Argument> valueArgument = Arrays.stream(method.getArguments())
                        .filter(arg -> !arg.getAnnotationMetadata().hasStereotype(Bindable.class)).findFirst();

                if (valueArgument.isPresent()) {
                    consumerConfiguration.setValueDeserializer(
                            pickDeserializer(valueArgument.get())
                    );
                } else {
                    //noinspection SingleStatementInBlock
                    consumerConfiguration.setValueDeserializer(new StringDeserializer());
                }
            }

            Consumer kafaConsumer = consumerMap.computeIfAbsent(method, executableMethod ->
                    beanContext.createBean(Consumer.class, consumerConfiguration)
            );

            for (Topic topicAnnotation : topicAnnotations) {
                String[] topicNames = topicAnnotation.value();
                if (ArrayUtils.isNotEmpty(topicNames)) {
                    if (consumerBean instanceof ConsumerRebalanceListener) {
                        kafaConsumer.subscribe(Arrays.asList(topicNames), (ConsumerRebalanceListener) consumerBean);
                    } else {
                        kafaConsumer.subscribe(Arrays.asList(topicNames));
                    }
                }

                String[] patterns = topicAnnotation.patterns();

                if (ArrayUtils.isNotEmpty(patterns)) {
                    for (String pattern : patterns) {
                        Pattern p = Pattern.compile(pattern);

                        if (consumerBean instanceof ConsumerRebalanceListener) {
                            kafaConsumer.subscribe(p, (ConsumerRebalanceListener) consumerBean);
                        } else {
                            kafaConsumer.subscribe(p);
                        }
                    }
                }
            }



            for (int i = 0; i < consumerThreads; i++) {
                executorService.submit(() -> {
                    try {
                        //noinspection InfiniteLoopStatement
                        while (true) {
                            ConsumerRecords<?, ?> consumerRecords = kafaConsumer.poll(pollTimeout.toMillis());

                            try {
                                if (consumerRecords != null && consumerRecords.count() > 0) {

                                    for (ConsumerRecord<?, ?> consumerRecord : consumerRecords) {
                                        BoundExecutable boundExecutable = executableBinder.bind(method, binderRegistry, consumerRecord);
                                        boundExecutable.invoke(
                                                consumerBean
                                        );

                                        if (offsetStrategy == OffsetStrategy.SYNC_PER_RECORD) {
                                            kafaConsumer.commitSync();
                                        } else if (offsetStrategy == OffsetStrategy.ASYNC_PER_RECORD) {
                                            kafaConsumer.commitAsync(resolveCommitCallback(consumerBean));
                                        }
                                    }
                                }

                                if (offsetStrategy == OffsetStrategy.SYNC) {
                                    kafaConsumer.commitSync();
                                } else if (offsetStrategy == OffsetStrategy.ASYNC) {
                                    kafaConsumer.commitAsync(resolveCommitCallback(consumerBean));
                                }
                            } catch (WakeupException e) {
                                throw e;
                            } catch (Throwable e) {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Kafka consumer [" + consumerBean + "] produced error: " + e.getMessage(), e);
                                }
                            }
                        }
                    } catch (WakeupException e) {
                        // ignore for shutdown
                    } finally {
                        try {
                            kafaConsumer.commitSync();
                        } finally {
                            kafaConsumer.close();
                        }
                    }
                });
            }
        }
    }

    private OffsetCommitCallback resolveCommitCallback(Object consumerBean) {
        return (offsets, exception) -> {
            if (consumerBean instanceof OffsetCommitCallback) {
                ((OffsetCommitCallback) consumerBean).onComplete(offsets, exception);
            } else if (exception != null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error asynchronously committing Kafka offsets for batch [" + offsets + "]: " + exception.getMessage(), exception);
                }
            }
        };
    }

    @Override
    @PreDestroy
    public void close() {
        for (Consumer consumer : consumerMap.values()) {
            consumer.wakeup();
        }
        consumerMap.clear();
    }

    private Deserializer pickDeserializer(Argument<?> argument) {
        Class<?> type = argument.getType();
        Deserializer deserializer;

        if (ClassUtils.isJavaLangType(type) || byte[].class == type) {
            Class wrapperType = ReflectionUtils.getWrapperType(type);
            deserializer = DEFAULT_DESERIALIZERS.get(wrapperType);
        } else {
            Optional<? extends Serde<?>> serde = serdeRegistry.getSerde(argument.getType());
            deserializer = serde.map(Serde::deserializer).orElse(null);
        }

        if (deserializer == null) {
            throw new SerializationException("No Kafka serializer found for argument: " + argument);
        }

        return deserializer;
    }
}
