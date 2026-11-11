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
package io.micronaut.discovery;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Looks up the services of a {@link DiscoveryClient} with {@link CompletionStage}s for the
 * framework: with the {@link AsyncDiscoveryClient} methods when the client implements it, and by
 * adapting the publisher methods otherwise. An {@link AsyncDiscoveryClient} method that returns
 * no stage, or a stage completed with {@code null}, as the methods of a mock may do, is replaced
 * by the publisher method. A method that throws completes the stage with its error.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DiscoveryClientStages {

    private DiscoveryClientStages() {
    }

    /**
     * @param discoveryClient The discovery client
     * @param serviceId       The service id
     * @return The stage of {@link AsyncDiscoveryClient#getInstancesAsync(String)}, or of {@link DiscoveryClient#getInstances(String)}
     */
    public static CompletionStage<List<ServiceInstance>> getInstances(DiscoveryClient discoveryClient, String serviceId) {
        try {
            if (discoveryClient instanceof AsyncDiscoveryClient asyncDiscoveryClient) {
                return CompletionStagePublishers.orElseIfNull(
                    asyncDiscoveryClient.getInstancesAsync(serviceId),
                    () -> CompletionStagePublishers.first(discoveryClient.getInstances(serviceId), Collections.emptyList())
                );
            }
            return CompletionStagePublishers.first(discoveryClient.getInstances(serviceId), Collections.emptyList());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * @param discoveryClient The discovery client
     * @return The stage of {@link AsyncDiscoveryClient#getServiceIdsAsync()}, or of {@link DiscoveryClient#getServiceIds()}
     */
    public static CompletionStage<List<String>> getServiceIds(DiscoveryClient discoveryClient) {
        try {
            if (discoveryClient instanceof AsyncDiscoveryClient asyncDiscoveryClient) {
                return CompletionStagePublishers.orElseIfNull(
                    asyncDiscoveryClient.getServiceIdsAsync(),
                    () -> CompletionStagePublishers.first(discoveryClient.getServiceIds(), Collections.emptyList())
                );
            }
            return CompletionStagePublishers.first(discoveryClient.getServiceIds(), Collections.emptyList());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
