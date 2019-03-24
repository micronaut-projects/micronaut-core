/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.util.StringUtils;
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
@Requires(property = JmxConfiguration.PREFIX + ".register-endpoints", notEquals = StringUtils.FALSE)
public class EndpointMethodJmxProcessor implements ExecutableMethodProcessor<Endpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMethodJmxProcessor.class);

    private final Map<BeanDefinition, MBeanDefinition> mBeanDefinitions = new HashMap<>(5);

    private final MBeanServer mBeanServer;
    private final NameGenerator nameGenerator;
    private final DynamicMBeanFactory mBeanFactory;
    private final BeanContext beanContext;

    /**
     * @param mBeanServer The server to register the endpoint beans with
     * @param nameGenerator The class to generate the bean names
     * @param mBeanFactory The factory to create the beans with
     * @param beanContext The bean context to retrieve the endpoint instance
     */
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
        mBeanDefinitions.compute(beanDefinition, (key, value) -> {
            if (value == null) {
                try {
                    value = new MBeanDefinition(key, new ArrayList<>());
                } catch (JMException e) {
                    LOG.error("Failed to generate an MBean name for the endpoint " + beanDefinition.getBeanType().getName(), e);
                }
            }
            if (value != null) {
                value.methods.add(method);
            }
            return value;
        });
    }

    /**
     * Registers the management beans.
     *
     * @param event The startup event
     */
    @EventListener
    void onStartup(StartupEvent event) {
        for (MBeanDefinition mBeanDefinition: mBeanDefinitions.values()) {
            BeanDefinition beanDefinition = mBeanDefinition.beanDefinition;

            try {
                Supplier<Object> instanceSupplier = SupplierUtil.memoized(() -> {
                    return beanContext.getBean(beanDefinition.getBeanType());
                });

                Object mBean = mBeanFactory.createMBean(beanDefinition, mBeanDefinition.methods, instanceSupplier);
                mBeanServer.registerMBean(mBean, mBeanDefinition.objectName);
            } catch (JMException e) {
                LOG.error("Failed to register an MBean for the endpoint " + beanDefinition.getBeanType().getName(), e);
            }
        }
    }

    /**
     * Un-registers the management beans.
     *
     * @param event The shutdown event
     */
    @EventListener
    void onShutdown(ShutdownEvent event) {
        for (MBeanDefinition mBeanDefinition: mBeanDefinitions.values()) {
            BeanDefinition beanDefinition = mBeanDefinition.beanDefinition;
            try {
                mBeanServer.unregisterMBean(mBeanDefinition.objectName);
            } catch (JMException e) {
                LOG.error("Failed to unregister an MBean for the endpoint " + beanDefinition.getBeanType().getName(), e);
            }
        }
    }

    /**
     * Internal cache.
     */
    private class MBeanDefinition {
        ObjectName objectName;
        BeanDefinition beanDefinition;
        List<ExecutableMethod> methods;

        MBeanDefinition(BeanDefinition beanDefinition, List<ExecutableMethod> methods) throws JMException {
            this.objectName = nameGenerator.generate(beanDefinition);
            this.beanDefinition = beanDefinition;
            this.methods = methods;
        }
    }
}
