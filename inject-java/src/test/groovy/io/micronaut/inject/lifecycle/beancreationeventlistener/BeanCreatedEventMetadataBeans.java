/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Prototype
@Requires(property = "spec.name", value = "BeanCreatedEventMetadata")
class EventRoot {
    final EventDependency dependency;

    EventRoot(EventDependency dependency) {
        this.dependency = dependency;
    }
}

@Prototype
@Requires(property = "spec.name", value = "BeanCreatedEventMetadata")
class EventDependency {
}

@Singleton
@Requires(property = "spec.name", value = "BeanCreatedEventMetadata")
class EventMetadataListener implements BeanCreatedEventListener<EventRoot> {
    static BeanDefinition<?> rootDefinition;
    static List<Class<?>> dependentTypes = List.of();

    @Override
    public EventRoot onCreated(BeanCreatedEvent<EventRoot> event) {
        rootDefinition = event.getRootBeanDefinition();
        List<Class<?>> types = new ArrayList<>();
        for (BeanRegistration<?> registration : event.getDependentBeans()) {
            types.add(registration.getBeanDefinition().getBeanType());
        }
        dependentTypes = List.copyOf(types);
        return event.getBean();
    }

    static void reset() {
        rootDefinition = null;
        dependentTypes = List.of();
    }
}

@Factory
@Requires(property = "spec.name", value = "BeanCreatedEventNull")
class NullEventFactory {
    @Prototype
    @Nullable
    NullEventProduct nullProduct() {
        return null;
    }
}

class NullEventProduct {
}

@Singleton
@Requires(property = "spec.name", value = "BeanCreatedEventNull")
class NullEventListener implements BeanCreatedEventListener<Object> {
    static boolean sawNullBean;

    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        sawNullBean |= event.getBean() == null;
        return event.getBean();
    }

    static void reset() {
        sawNullBean = false;
    }
}
