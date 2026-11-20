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
package io.micronaut.http.server.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.limit.Acquisition;
import io.micronaut.http.server.limit.ConcurrencyRequestLimiter;
import io.micronaut.http.server.limit.RequestLimitExceededException;
import io.micronaut.http.server.limit.RequestLimiter;
import io.micronaut.http.server.limit.RequestLimiterConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RequestLimitFilterTest {

    @Test
    void aLimiterThatThrowsGivesBackThePermitsTakenBeforeIt() {
        ConcurrencyRequestLimiter first = new ConcurrencyRequestLimiter("first", 1, null);
        RequestLimiter failing = new FailingLimiter("failing");
        RequestLimitFilter filter = new RequestLimitFilter(List.of(first, failing), List.of(config("first"), config("failing")));
        for (int i = 0; i < 3; i++) {
            Assertions.assertThrows(IllegalStateException.class, () -> filter.filterRequest(HttpRequest.GET("/any")));
            Assertions.assertEquals(0, first.getInFlight(), "A permit leaked");
        }
    }

    @Test
    void aRejectionGivesBackThePermitsTakenBeforeIt() {
        ConcurrencyRequestLimiter first = new ConcurrencyRequestLimiter("first", 1, null);
        ConcurrencyRequestLimiter full = new ConcurrencyRequestLimiter("full", 1, null);
        Acquisition.Permit held = (Acquisition.Permit) full.tryAcquire(HttpRequest.GET("/"));
        RequestLimitFilter filter = new RequestLimitFilter(List.of(first, full), List.of(config("first"), config("full")));
        RequestLimitExceededException e = Assertions.assertThrows(RequestLimitExceededException.class, () -> filter.filterRequest(HttpRequest.GET("/any")));
        Assertions.assertEquals("full", e.getLimiterName());
        Assertions.assertEquals(0, first.getInFlight());
        held.release();
    }

    private static RequestLimiterConfiguration config(String name) {
        RequestLimiterConfiguration configuration = new RequestLimiterConfiguration(name);
        configuration.setPatterns(List.of("/**"));
        return configuration;
    }

    private record FailingLimiter(String getName) implements RequestLimiter {
        @Override
        public Acquisition tryAcquire(HttpRequest<?> request) {
            throw new IllegalStateException("broken limiter");
        }

        @Override
        public int getInFlight() {
            return 0;
        }

        @Override
        public long getAvailablePermits() {
            return -1;
        }

        @Override
        public long getAcquiredCount() {
            return 0;
        }

        @Override
        public long getRejectedCount() {
            return 0;
        }
    }
}
