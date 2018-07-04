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

package io.micronaut.configuration.kafka.scope;

import io.micronaut.configuration.kafka.KafkaProducerRegistry;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration;
import io.micronaut.configuration.kafka.config.DefaultKafkaProducerConfiguration;
import io.micronaut.configuration.kafka.serde.SerdeRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.ParametrizedProvider;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A scope implementation for injecting {@link org.apache.kafka.clients.producer.KafkaProducer} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Singleton
public class KafkaClientScope implements CustomScope<KafkaClient>, LifeCycle<KafkaClientScope>, KafkaProducerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientScope.class);
    private final Map<ClientKey, KafkaProducer> clients = new ConcurrentHashMap<>();

    private final BeanContext beanContext;
    private final SerdeRegistry serdeRegistry;

    /**
     * Constructs a new client scope.
     *
     * @param beanContext The bean context
     * @param serdeRegistry The serde registry
     */
    public KafkaClientScope(
            BeanContext beanContext,
            SerdeRegistry serdeRegistry) {
        this.beanContext = beanContext;
        this.serdeRegistry = serdeRegistry;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Class<KafkaClient> annotationType() {
        return KafkaClient.class;
    }

    @Override
    public <T> T get(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        BeanResolutionContext.Segment segment = resolutionContext.getPath().currentSegment().orElseThrow(() ->
                new IllegalStateException("@KafkaClient used in invalid location")
        );
        Argument argument = segment.getArgument();
        KafkaClient annotation = argument.getAnnotation(KafkaClient.class);
        if (annotation == null) {
            throw new DependencyInjectionException(resolutionContext, argument, "KafkaClientScope called for injection point that is not annotated with @KafkaClient");
        }
        if (!Producer.class.isAssignableFrom(argument.getType())) {
            throw new DependencyInjectionException(resolutionContext, argument, "@KafkaClient used on type that is not a " + Producer.class.getName());
        }
        if (!(provider instanceof ParametrizedProvider)) {
            throw new DependencyInjectionException(resolutionContext, argument, "KafkaClientScope called with invalid bean provider");
        }

        Optional<Argument<?>> k = argument.getTypeVariable("K");
        Optional<Argument<?>> v = argument.getTypeVariable("V");

        if (!k.isPresent() || !v.isPresent()) {
            throw new DependencyInjectionException(resolutionContext, argument, "@KafkaClient used on type missing generic argument values for Key and Value");

        }

        String id = annotation.id();
        Argument<?> keyArgument = k.get();
        Argument<?> valueArgument = v.get();
        return getKafkaProducer(id, keyArgument, valueArgument);
    }

    @Nonnull
    @Override
    public <K, V> KafkaProducer getProducer(String id, Argument<K> keyType, Argument<V> valueType) {
        return getKafkaProducer(id, keyType, valueType);
    }

    @SuppressWarnings("unchecked")
    private <T> T getKafkaProducer(@Nullable String id, Argument<?> keyType, Argument<?> valueType) {
        ClientKey key = new ClientKey(
                id,
                keyType.getType(),
                valueType.getType()
        );

        return (T) clients.computeIfAbsent(key, clientKey -> {
            Supplier<AbstractKafkaProducerConfiguration> defaultResolver = () -> beanContext.getBean(DefaultKafkaProducerConfiguration.class);
            AbstractKafkaProducerConfiguration config;
            boolean hasId = StringUtils.isNotEmpty(id);
            if (hasId) {
                config = beanContext.findBean(
                        AbstractKafkaProducerConfiguration.class,
                        Qualifiers.byName(id)
                ).orElseGet(defaultResolver);
            } else {
                config = defaultResolver.get();
            }

            DefaultKafkaProducerConfiguration newConfig = new DefaultKafkaProducerConfiguration(config);

            Properties properties = newConfig.getConfig();
            if (!properties.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)) {
                Serializer<?> keySerializer = serdeRegistry.pickSerializer(keyType);
                newConfig.setKeySerializer(keySerializer);
            }

            if (!properties.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
                Serializer<?> valueSerializer = serdeRegistry.pickSerializer(valueType);
                newConfig.setValueSerializer(valueSerializer);
            }


            if (hasId) {
                properties.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, id);
            }
            return beanContext.createBean(KafkaProducer.class, newConfig);
        });
    }

    @Override
    public KafkaClientScope stop() {
        for (KafkaProducer producer : clients.values()) {
            try {
                producer.close();
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down Kafka producer: " + e.getMessage(), e);
                }
            }
        }
        clients.clear();
        return this;
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.empty();
    }


    /**
     * key for retrieving built producers.
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    private class ClientKey {
        private final String id;
        private final Class keyType;
        private final Class valueType;

        ClientKey(String id, Class keyType, Class valueType) {
            this.id = id;
            this.keyType = keyType;
            this.valueType = valueType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClientKey clientKey = (ClientKey) o;
            return Objects.equals(id, clientKey.id) &&
                    Objects.equals(keyType, clientKey.keyType) &&
                    Objects.equals(valueType, clientKey.valueType);
        }

        @Override
        public int hashCode() {

            return Objects.hash(id, keyType, valueType);
        }
    }
}
