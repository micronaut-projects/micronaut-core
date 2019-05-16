/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.originatingips;

import io.micronaut.http.HttpRequest;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregator of beans of type {@link IpAddressesResolver}.
 *
 * @author Sergio del Amo
 * @since 1.2.0
 */
@Singleton
public class IpAddressesResolverAggregator {

    private Collection<IpAddressesResolver> ipAddressesResolvers;

    /**
     *
     * @param ipAddressesResolvers Beans of type {@link IpAddressesResolver} in the context.
     */
    public IpAddressesResolverAggregator(Collection<IpAddressesResolver> ipAddressesResolvers) {
        this.ipAddressesResolvers = ipAddressesResolvers;
    }

    /**
     *
     * @param request Http Request
     * @return List of originating IP Addresses
     */
    public List<String> originatingIpAddresses(HttpRequest<?> request) {
        return ipAddressesResolvers.stream()
                .map(resolver -> resolver.originatingIpAddres(request))
                .collect(Collectors.toList())
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
