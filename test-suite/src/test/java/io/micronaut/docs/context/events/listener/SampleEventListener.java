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
package io.micronaut.docs.context.events.listener;

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.runtime.event.annotation.EventListener;
// end::imports[]
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class SampleEventListener {
    private int invocationCounter = 0;

    @EventListener
    public void onSampleEvent(SampleEvent event) {
        invocationCounter++;
    }

    @EventListener
    public void onStartupEvent(StartupEvent event) {
        // startup logic here
    }

    @EventListener
    public void onShutdownEvent(ShutdownEvent event) {
        // shutdown logic here
    }

    public int getInvocationCounter() {
        return invocationCounter;
    }
}
// end::class[]
