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

package io.micronaut.configuration.kafka.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.configuration.kafka.config.AbstractKafkaConsumerConfiguration;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * @author graemerocher
 * @since 1.0
 */
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".kafka.enabled", value = "true", defaultValue = "true")
@Context
public class KafkaProducerMetrics implements BeanCreatedEventListener<AbstractKafkaConsumerConfiguration>, MeterBinder, Closeable {

    private static final Collection<MeterRegistry> METER_REGISTRIES = new ConcurrentLinkedQueue<>();

    @Override
    public AbstractKafkaConsumerConfiguration onCreated(BeanCreatedEvent<AbstractKafkaConsumerConfiguration> event) {

        Properties props = event.getBean().getConfig();

        if (!props.containsKey(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG)) {
            props.put(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, KafkaConsumerMetrics.Reporter.class.getName());
        }
        return event.getBean();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        METER_REGISTRIES.add(registry);
    }

    @Override
    @PreDestroy
    public void close() {
        METER_REGISTRIES.clear();
    }

    /**
     * A {@link MetricsReporter} that binds metrics to micrometer.
     */
    @Internal
    public static class Reporter implements MetricsReporter {

        private List<KafkaMetric> metrics;

        @Override
        public void init(List<KafkaMetric> metrics) {
            this.metrics = metrics;
            for (MeterRegistry meterRegistry : KafkaProducerMetrics.METER_REGISTRIES) {
                for (KafkaMetric metric : metrics) {
                    registerMetric(meterRegistry, metric);
                }
            }
        }

        @Override
        public void metricChange(KafkaMetric metric) {
            for (MeterRegistry meterRegistry : KafkaProducerMetrics.METER_REGISTRIES) {
                registerMetric(meterRegistry, metric);
            }
        }

        @Override
        public void metricRemoval(KafkaMetric metric) {
            // no-op (Micrometer doesn't support removal)
        }

        @Override
        public void close() {
            if (metrics != null) {
                metrics.clear();
                metrics = null;
            }
        }

        @Override
        public void configure(Map<String, ?> configs) {

        }

        private void registerMetric(MeterRegistry meterRegistry, KafkaMetric metric) {
            MetricName metricName = metric.metricName();
            Object v = metric.metricValue();
            if (v instanceof Double) {
                List<Tag> tags = metricName
                        .tags()
                        .entrySet()
                        .stream()
                        .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
                String name = AbstractKafkaConfiguration.PREFIX + '.' + metricName.name();
                meterRegistry.gauge(name, tags, metric, value -> (Double) value.metricValue());
            }
        }
    }
}

