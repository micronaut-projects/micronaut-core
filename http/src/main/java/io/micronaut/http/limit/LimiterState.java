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
package io.micronaut.http.limit;

import io.micronaut.core.annotation.Experimental;

/**
 * A read-only view of the state of a limiter of the server or of the client, for a metrics
 * module to read. All methods are safe to call concurrently and do not block.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface LimiterState {

    /**
     * @return The name of the limiter
     */
    String getName();

    /**
     * @return The number of permits currently held, i.e. the requests or exchanges in flight
     * that the limiter counts. Always {@code 0} for a limiter that does not track in-flight work,
     * like a rate limiter.
     */
    int getInFlight();

    /**
     * @return The number of permits available right now, or {@code -1} if unknown
     */
    long getAvailablePermits();

    /**
     * @return The number of callers waiting for a permit, {@code 0} for a limiter without a queue
     */
    default int getQueued() {
        return 0;
    }

    /**
     * @return The total number of permits granted since the limiter was created
     */
    long getAcquiredCount();

    /**
     * @return The total number of rejected acquisitions since the limiter was created
     */
    long getRejectedCount();
}
