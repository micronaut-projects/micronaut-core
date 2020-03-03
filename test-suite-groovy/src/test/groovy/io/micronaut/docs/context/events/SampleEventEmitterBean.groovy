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
package io.micronaut.docs.context.events

// tag::class[]
import io.micronaut.context.event.ApplicationEventPublisher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleEventEmitterBean {

    @Inject
    ApplicationEventPublisher eventPublisher

    void publishSampleEvent() {
        eventPublisher.publishEvent(new SampleEvent())
    }

}
// end::class[]