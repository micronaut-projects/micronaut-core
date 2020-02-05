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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URL;

/**
 * A {@link LoadBalancer} that resolves a fixed URL.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FixedLoadBalancer implements LoadBalancer  {
    private final Publisher<ServiceInstance> publisher;
    private final URL url;

    /**
     * Constructs a new FixedLoadBalancer.
     *
     * @param url The URL to fix to
     */
    public FixedLoadBalancer(URL url) {
        this.url = url;
        this.publisher = Publishers.just(ServiceInstance.of(url.getHost(), url));
    }

    @Override
    public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
        return publisher;
    }

    /**
     * @return The URL of the {@link LoadBalancer}
     */
    public URL getUrl() {
        return url;
    }
}
