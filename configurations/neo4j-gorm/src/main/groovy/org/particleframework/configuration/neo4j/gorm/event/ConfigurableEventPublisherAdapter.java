/*
 * Copyright 2018 original authors
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
package org.particleframework.configuration.neo4j.gorm.event;

import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.spring.core.event.ApplicationEventPublisherAdapter;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Adapts Spring event model
 *
 * @author graemerocher
 * @since 1.0
 */
public class ConfigurableEventPublisherAdapter extends ApplicationEventPublisherAdapter implements ConfigurableApplicationEventPublisher {
    private final ApplicationContext applicationContext;

    public ConfigurableEventPublisherAdapter(ApplicationContext applicationContext) {
        super(applicationContext);
        this.applicationContext = applicationContext;
    }

    @Override
    public void addApplicationListener(ApplicationListener<?> listener) {
        if(listener instanceof SmartApplicationListener) {
            SmartApplicationListener smartApplicationListener = (SmartApplicationListener) listener;
            this.applicationContext.registerSingleton(
                    ApplicationEventListener.class,
                    new ApplicationEventListener() {
                        @Override
                        public void onApplicationEvent(Object event) {
                            if(event instanceof ApplicationEvent) {
                                smartApplicationListener.onApplicationEvent((ApplicationEvent) event);
                            }
                        }

                        @Override
                        public boolean supports(Object event) {
                            if(event instanceof ApplicationEvent) {
                                ApplicationEvent applicationEvent = (ApplicationEvent) event;

                                Object source = applicationEvent.getSource();
                                return smartApplicationListener.supportsEventType(applicationEvent.getClass())
                                        && (source == null || smartApplicationListener.supportsSourceType(source.getClass()));
                            }
                            return false;
                        }
                    }
            );

        }
    }
}
