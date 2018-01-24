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

import org.particleframework.discovery.consul.v1.ConsulClient;
import org.particleframework.http.client.ServerSelector;
import org.particleframework.http.client.ServerSelectorProvider;
import org.particleframework.http.client.exceptions.HttpClientException;

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
public class ConsulServerSelectorProvider implements ServerSelectorProvider{

    private final ConsulConfiguration configuration;

    public ConsulServerSelectorProvider(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getId() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public ServerSelector getSelector() {
        return discriminator -> {
            String spec = "http://" + configuration.getHost() + ":" + configuration.getPort();
            try {
                return new URL(spec);
            } catch (MalformedURLException e) {
                throw new HttpClientException("Invalid Consul URL: " + spec, e);
            }
        };
    }
}
