/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.messaging;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.naming.Described;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.annotation.MessageListener;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.runtime.exceptions.ApplicationStartupException;

import javax.inject.Singleton;
import java.util.Collection;

/**
 * An alternative {@link EmbeddedApplication} that gets activated for messaging applications when
 * no other application is present.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(missingBeans = EmbeddedApplication.class)
public class MessagingApplication implements EmbeddedApplication, Described {

    private final ApplicationContext applicationContext;
    private final ApplicationConfiguration configuration;

    /**
     * Constructs a new messaging application.
     *
     * @param applicationContext The context
     * @param configuration The configuration
     */
    public MessagingApplication(ApplicationContext applicationContext, ApplicationConfiguration configuration) {
        this.applicationContext = applicationContext;
        this.configuration = configuration;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration() {
        return configuration;
    }

    @Override
    public boolean isRunning() {
        return applicationContext.isRunning();
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public MessagingApplication start() {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext != null && !applicationContext.isRunning()) {
            try {
                applicationContext.start();
                applicationContext.publishEvent(new ApplicationStartupEvent(this));
            } catch (Throwable e) {
                throw new ApplicationStartupException("Error starting messaging server: " + e.getMessage(), e);
            }
        }
        return this;
    }

    @Override
    public ApplicationContextLifeCycle stop() {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext != null && applicationContext.isRunning()) {
            applicationContext.stop();
            applicationContext.publishEvent(new ApplicationShutdownEvent(this));
        }
        return this;
    }

    @Override
    public String getDescription() {
        Collection<BeanDefinition<?>> beanDefinitions = applicationContext.getBeanDefinitions(Qualifiers.byStereotype(MessageListener.class));
        return beanDefinitions.size() + " active message listeners.";
    }
}
