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
package io.micronaut.discovery;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.naming.Described;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.util.List;

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
}
