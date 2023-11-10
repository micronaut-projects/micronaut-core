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

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.context.events.SampleEventEmitterBean;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
// end::imports[]

// tag::class[]
class SampleEventListenerSpec {

    @Test
    void testEventListenerIsNotified() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SampleEventEmitterBean emitter = context.getBean(SampleEventEmitterBean.class);
            SampleEventListener listener = context.getBean(SampleEventListener.class);
            assertEquals(0, listener.getInvocationCounter());
            emitter.publishSampleEvent();
            await().atMost(5, SECONDS).until(listener::getInvocationCounter, equalTo(1));
        }
    }
}
// end::class[]
