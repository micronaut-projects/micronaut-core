/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul;

import org.particleframework.discovery.consul.condition.RequiresConsul;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.client.LoadBalancerProvider;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.discovery.consul.client.v1.ConsulClient;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A selector that discovers the consul URI from configuration
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresConsul
public class ConsulLoadBalancerProvider implements LoadBalancerProvider {

    private final ConsulConfiguration configuration;

    public ConsulLoadBalancerProvider(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getId() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public LoadBalancer getLoadBalancer() {
        return discriminator -> {
            String spec = (configuration.isSecure() ? "https" : "http") + "://" + configuration.getHost() + ":" + configuration.getPort();
            try {
                return new URL(spec);
            } catch (MalformedURLException e) {
                throw new HttpClientException("Invalid Consul URL: " + spec, e);
            }
        };
    }
}
