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

import java.util.List;

/**
 * The default {@link CompositeDiscoveryClient} that is activated when caching is disabled.
 *
 * <p>The bean is an {@link AsyncCompositeDiscoveryClient}, which combines the
 * {@link java.util.concurrent.CompletionStage}s of the discovery clients without a publisher.
 * This class only implements the publisher methods, so that a subclass that overrides them, and
 * replaces the bean, is called through them by the default
 * {@link #getInstancesAsync(String)} and {@link #getServiceIdsAsync()}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultCompositeDiscoveryClient extends CompositeDiscoveryClient {

    /**
     * Create a default composite discovery for the discovery clients.
     *
     * @param discoveryClients The Discovery clients used for service discovery
     */
    public DefaultCompositeDiscoveryClient(List<DiscoveryClient> discoveryClients) {
        super(discoveryClients.toArray(new DiscoveryClient[0]));
    }

    /**
     * Create a default composite discovery for the discovery clients.
     *
     * @param discoveryClients The Discovery clients used for service discovery
     */
    public DefaultCompositeDiscoveryClient(DiscoveryClient... discoveryClients) {
        super(discoveryClients);
    }
}
