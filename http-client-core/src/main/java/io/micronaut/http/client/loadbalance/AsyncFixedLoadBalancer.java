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

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link FixedLoadBalancer} of {@link io.micronaut.http.client.LoadBalancer#fixed(URI)},
 * which selects its instance without a publisher. It is final, so that a subclass of
 * {@link FixedLoadBalancer} that overrides {@link #select(Object)} is selected through that
 * method, by the default {@link #selectAsync(Object)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AsyncFixedLoadBalancer extends FixedLoadBalancer {

    /**
     * @param uri The URI to fix to
     */
    public AsyncFixedLoadBalancer(URI uri) {
        super(uri);
    }

    @Override
    public CompletionStage<ServiceInstance> selectAsync(@Nullable Object discriminator) {
        return CompletableFuture.completedFuture(getServiceInstance());
    }
}
