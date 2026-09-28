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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * A discriminator of {@link io.micronaut.http.client.LoadBalancer#select(Object)} that leaves
 * instances out of the selection, e.g. the instances a retry already tried: a round-robin load
 * balancer selects one of them only when every available instance is left out. The
 * {@link LoadBalancerKey key} of the discriminator it wraps goes to the {@link LoadBalancerStrategy}.
 *
 * @param uris          The URIs of the instances to leave out
 * @param discriminator The discriminator of the selection, if any
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record ExcludedInstances(Set<URI> uris, @Nullable Object discriminator) {

    /**
     * @param uris          The URIs of the instances to leave out
     * @param discriminator The discriminator of the selection
     */
    public ExcludedInstances {
        uris = Set.copyOf(Objects.requireNonNull(uris, "uris"));
    }
}
