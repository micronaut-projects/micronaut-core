/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.lifecycle.beancreationlambda;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.StartupEvent;

import javax.inject.Singleton;

@Factory
public class ListenerFactory {

    @Singleton
    BeanCreatedEventListener<B> onCreateB() {
        return event -> {
            ChildB childB = new ChildB(event.getBean());
            childB.name = "good";
            return childB;
        };
    }

    @Singleton
    ApplicationEventListener<StartupEvent> onStartup() {
        return event -> System.out.println("Starting up!");
    }
}
