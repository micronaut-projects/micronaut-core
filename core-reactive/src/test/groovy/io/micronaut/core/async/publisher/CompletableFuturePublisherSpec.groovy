/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.async.publisher

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class CompletableFuturePublisherSpec extends Specification {

    void "test the error is emitted when thrown from the supplier"() {
        when:
        Publisher publisher = Publishers.fromCompletableFuture({ -> throw new RuntimeException() })
        PollingConditions conditions = new PollingConditions()
        Throwable error
        publisher.subscribe(new Subscriber<Object>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(Object o) {}

            @Override
            void onError(Throwable t) {
                error = t
            }

            @Override
            void onComplete() {}
        })

        then:
        conditions.eventually {
            error instanceof RuntimeException
        }
    }
}
