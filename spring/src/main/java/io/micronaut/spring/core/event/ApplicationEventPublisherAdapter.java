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
package io.micronaut.spring.core.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * An adapter for Spring's {@link ApplicationEventPublisher} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
public class ApplicationEventPublisherAdapter implements ApplicationEventPublisher {

    private final io.micronaut.context.event.ApplicationEventPublisher eventPublisher;

    /**
     * Constructor.
     *
     * @param eventPublisher The application event publisher
     */
    public ApplicationEventPublisherAdapter(io.micronaut.context.event.ApplicationEventPublisher eventPublisher) {
        if (eventPublisher == null) {
            throw new IllegalArgumentException("Event publisher must be specified");
        }
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publishEvent(ApplicationEvent event) {
        this.eventPublisher.publishEvent(event);
    }

    @Override
    public void publishEvent(Object event) {
        this.eventPublisher.publishEvent(event);
    }
}
