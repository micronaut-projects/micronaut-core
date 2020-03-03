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
package io.micronaut.management.endpoint.info;

import org.reactivestreams.Publisher;

/**
 * <p>Aggregates all registered info sources into a single response.</p>
 * <p>In case of conflicts, priority is set based on the order of info sources {@link io.micronaut.core.order.Ordered}</p>
 *
 * @param <T> The type
 * @author Zachary Klein
 * @since 1.0
 */
public interface InfoAggregator<T> {

    /**
     * Aggregate an array of {@link InfoSource} and return a publisher.
     *
     * @param sources an array of InfoSources
     * @return A {@link Publisher} of <code>T</code>
     */
    Publisher<T> aggregate(InfoSource[] sources);
}
