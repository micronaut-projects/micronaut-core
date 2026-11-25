/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class LoadBalancerSelectionTest {

    private static final ServiceInstance INSTANCE = ServiceInstance.of("svc", URI.create("http://localhost:1"));

    @Test
    void theOutcomeIsReportedOnceAndEndsTheExchange() {
        List<LoadBalancer.Outcome> outcomes = new ArrayList<>();
        AtomicInteger ends = new AtomicInteger();
        LoadBalancerSelection selection = new LoadBalancerSelection(recording(outcomes), INSTANCE, ends::incrementAndGet);
        selection.report(LoadBalancer.Outcome.SUCCESS);
        selection.release();
        selection.end();
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS), outcomes);
        Assertions.assertEquals(1, ends.get());
    }

    @Test
    void aHeldEndWaitsForTheExplicitEnd() {
        AtomicInteger ends = new AtomicInteger();
        LoadBalancerSelection selection = new LoadBalancerSelection(recording(new ArrayList<>()), INSTANCE, ends::incrementAndGet);
        selection.holdEnd();
        selection.report(LoadBalancer.Outcome.SUCCESS);
        Assertions.assertEquals(0, ends.get());
        selection.end();
        selection.end();
        Assertions.assertEquals(1, ends.get());
    }

    @Test
    void aRetainedEndRunsOnceBothExchangesEnded() {
        AtomicInteger ends = new AtomicInteger();
        LoadBalancerSelection selection = new LoadBalancerSelection(recording(new ArrayList<>()), INSTANCE, ends::incrementAndGet);
        Runnable later = selection.retainEnd();
        Assertions.assertNotNull(later);
        selection.end();
        Assertions.assertEquals(0, ends.get(), "The later exchange still holds the end");
        later.run();
        later.run();
        Assertions.assertEquals(1, ends.get());
        // once ended, nothing can be retained any more
        Assertions.assertNull(selection.retainEnd());
    }

    @Test
    void aSelectionWithoutEndActionRetainsNothing() {
        LoadBalancerSelection selection = new LoadBalancerSelection(recording(new ArrayList<>()), INSTANCE);
        Assertions.assertNull(selection.retainEnd());
        selection.end();
        selection.claim();
        selection.releaseUnclaimed();
    }

    @Test
    void anUnclaimedSelectionIsReleased() {
        List<LoadBalancer.Outcome> outcomes = new ArrayList<>();
        LoadBalancerSelection selection = new LoadBalancerSelection(recording(outcomes), INSTANCE);
        selection.releaseUnclaimed();
        Assertions.assertEquals(List.of(LoadBalancer.Outcome.CANCELLED), outcomes);
        Assertions.assertSame(INSTANCE, selection.instance());
    }

    private static LoadBalancer recording(List<LoadBalancer.Outcome> outcomes) {
        return new LoadBalancer() {
            @Override
            public org.reactivestreams.Publisher<ServiceInstance> select(Object discriminator) {
                return Publishers.just(INSTANCE);
            }

            @Override
            public void report(ServiceInstance serviceInstance, Outcome outcome) {
                outcomes.add(outcome);
            }
        };
    }
}
