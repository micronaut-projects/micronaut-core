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
package io.micronaut.docs.context.events.async;

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
// end::imports[]
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

// tag::class[]
@Singleton
public class SampleEventListener {
    private AtomicInteger invocationCounter = new AtomicInteger(0);

    @EventListener
    @Async
    public void onSampleEvent(SampleEvent event) {
        invocationCounter.getAndIncrement();
    }

    public int getInvocationCounter() {
        return invocationCounter.get();
    }
}
// end::class[]
