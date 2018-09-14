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

package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.configuration.jmx.JmxConfiguration;
import io.micronaut.configuration.jmx.context.DynamicMBeanFactory;
import io.micronaut.configuration.jmx.context.NameGenerator;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.runtime.event.annotation.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.management.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Registers endpoint methods with JMX.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(property = JmxConfiguration.PREFIX + ".registerEndpoints", defaultValue = "true")
public class EndpointMethodJmxProcessor implements ExecutableMethodProcessor<Endpoint> {

    private final Map<BeanDefinition, List<ExecutableMethod>> methods = new HashMap<>(5);

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMethodJmxProcessor.class);

    private final MBeanServer mBeanServer;
    private final NameGenerator nameGenerator;
    private final DynamicMBeanFactory mBeanFactory;
    private final BeanContext beanContext;

    public EndpointMethodJmxProcessor(MBeanServer mBeanServer,
                                      @Named("endpoint") NameGenerator nameGenerator,
                                      @Named("endpoint") DynamicMBeanFactory mBeanFactory,
                                      BeanContext beanContext) {
        this.mBeanServer = mBeanServer;
        this.nameGenerator = nameGenerator;
        this.mBeanFactory = mBeanFactory;
        this.beanContext = beanContext;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        methods.compute(beanDefinition, (key, value) -> {
            if (value == null) {
                value = new ArrayList<>(1);
            }
            value.add(method);
            return value;
        });
    }

    @EventListener
    void onStartup(StartupEvent event) {

        for (Map.Entry<BeanDefinition, List<ExecutableMethod>> entry: methods.entrySet()) {
            BeanDefinition beanDefinition = entry.getKey();

            try {
                Supplier<Object> instanceSupplier = SupplierUtil.memoized(() -> {
                    return beanContext.getBean(beanDefinition.getBeanType());
                });

                Object mBean = mBeanFactory.createMBean(beanDefinition, entry.getValue(), instanceSupplier);

                mBeanServer.registerMBean(mBean, nameGenerator.generate(beanDefinition));
            } catch (JMException e) {
                LOG.error("Failed to register an MBean for the endpoint " + beanDefinition.getBeanType().getName(), e);
            }
        }
    }
}
