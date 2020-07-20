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
package io.micronaut.runtime.event.annotation.itfce;

import io.micronaut.context.event.ApplicationEventPublisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DefaultThingService implements ThingCreatedEventListener {

    private List<String> things = new ArrayList<>();

    private ApplicationEventPublisher publisher;

    @Inject
    public DefaultThingService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void create(String thing) {
        publisher.publishEvent(new ThingCreatedEvent(thing));
    }

    @Override
    public void onThingCreated(ThingCreatedEvent event) {
        String thing = event.getThing();
        things.add(thing);
    }

    public List<String> getThings() {
        return things;
    }
}
