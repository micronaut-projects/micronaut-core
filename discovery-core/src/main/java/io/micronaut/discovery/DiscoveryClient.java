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
package io.micronaut.discovery;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.naming.Described;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Main client abstraction used for service discovery.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(DiscoveryClient.class)
public interface DiscoveryClient extends Closeable, AutoCloseable, Described {

    /**
     * Obtain a list of {@link ServiceInstance} for the given service id.
     *
     * @param serviceId The service id
     * @return A {@link Publisher} that emits a list of {@link ServiceInstance}
     */
    @SingleResult
    Publisher<List<ServiceInstance>> getInstances(String serviceId);

    /**
     * @return The known service IDs
     */
    @SingleResult
    Publisher<List<String>> getServiceIds();

    /**
     * The {@link CompletionStage} counterpart of {@link #getInstances(String)}. By default, it
     * adapts the single result of {@link #getInstances(String)}, an empty list when the publisher
     * completes without a result. Cancelling the stage cancels the subscription.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. The
     * framework never cancels a stage it did not create: it ignores its result instead.</p>
     *
     * @param serviceId The service id
     * @return A {@link CompletionStage} completed with the list of {@link ServiceInstance}
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
        return CompletionStagePublishers.first(getInstances(serviceId), Collections.emptyList());
    }

    /**
     * The {@link CompletionStage} counterpart of {@link #getServiceIds()}. By default, it adapts
     * the single result of {@link #getServiceIds()}, an empty list when the publisher completes
     * without a result. Cancelling the stage cancels the subscription.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. The
     * framework never cancels a stage it did not create: it ignores its result instead.</p>
     *
     * @return A {@link CompletionStage} completed with the known service IDs
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<List<String>> getServiceIdsAsync() {
        return CompletionStagePublishers.first(getServiceIds(), Collections.emptyList());
    }
}
