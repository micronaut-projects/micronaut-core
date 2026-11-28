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
package io.micronaut.retry;

import io.micronaut.core.annotation.Experimental;

import java.util.Optional;
import java.util.Set;

/**
 * The named bulkheads of the application, which the methods annotated
 * {@code @Bulkhead(name = "...")} share.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public interface BulkheadRegistry {

    /**
     * @return The names of the bulkheads that a call has reached so far
     */
    Set<String> getNames();

    /**
     * @param name The name of a bulkhead
     * @return How full it is, or an empty optional when no call has reached it yet
     */
    Optional<BulkheadSnapshot> findSnapshot(String name);
}
