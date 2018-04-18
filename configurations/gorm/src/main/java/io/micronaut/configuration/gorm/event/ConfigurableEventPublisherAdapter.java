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

package io.micronaut.configuration.gorm.event;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.spring.core.event.ApplicationEventPublisherAdapter;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Adapts Spring event model.
 *
 * @author graemerocher
 * @since 1.0
 */
public class ConfigurableEventPublisherAdapter extends ApplicationEventPublisherAdapter implements ConfigurableApplicationEventPublisher {
    private final ApplicationContext applicationContext;

    /**
     * Constructor.
     * @param applicationContext applicationContext
     */
    public ConfigurableEventPublisherAdapter(ApplicationContext applicationContext) {
        super(applicationContext);
        this.applicationContext = applicationContext;
    }

    @Override
    public void addApplicationListener(ApplicationListener<?> listener) {
        if (listener instanceof SmartApplicationListener) {
            SmartApplicationListener smartApplicationListener = (SmartApplicationListener) listener;
            Qualifier<ApplicationEventListener> qualifier = resolveQualifier(smartApplicationListener);
            this.applicationContext.registerSingleton(
                ApplicationEventListener.class,
                new ApplicationEventListener() {
                    @Override
                    public void onApplicationEvent(Object event) {
                        if (event instanceof ApplicationEvent) {
                            smartApplicationListener.onApplicationEvent((ApplicationEvent) event);
                        }
                    }

                    @Override
                    public boolean supports(Object event) {
                        if (event instanceof ApplicationEvent) {
                            ApplicationEvent applicationEvent = (ApplicationEvent) event;

                            Object source = applicationEvent.getSource();
                            return smartApplicationListener.supportsEventType(applicationEvent.getClass())
                                && (source == null || smartApplicationListener.supportsSourceType(source.getClass()));
                        }
                        return false;
                    }

                    @Override
                    public String toString() {
                        return "Adapted: " + smartApplicationListener;
                    }
                },
                qualifier
            );

        }
    }

    private Qualifier<ApplicationEventListener> resolveQualifier(SmartApplicationListener smartApplicationListener) {
        return Qualifiers.byName(smartApplicationListener.getClass().getSimpleName());
    }
}
