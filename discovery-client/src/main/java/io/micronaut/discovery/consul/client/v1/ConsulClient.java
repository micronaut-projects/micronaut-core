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
package io.micronaut.discovery.consul.client.v1;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

/**
 * A non-blocking HTTP client for consul.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConsulClient extends ConsulOperations, DiscoveryClient {

    /**
     * The default ID of the consul service.
     */
    String SERVICE_ID = "consul";

    @Override
    default Publisher<List<String>> getServiceIds() {
        return Publishers.map(getServiceNames(), services -> new ArrayList<>(services.keySet()));
    }
}
