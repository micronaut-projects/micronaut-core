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
package org.particleframework.discovery.consul.client.v1;

import org.particleframework.core.util.Toggleable;
import org.particleframework.discovery.consul.ConsulConfiguration;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MutableHttpRequest;
import org.particleframework.http.annotation.Filter;
import org.particleframework.http.filter.ClientFilterChain;
import org.particleframework.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * A {@link HttpClientFilter} that adds the {@link #HEADER_CONSUL_TOKEN} header
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter(patterns = "/v1/**", clients = ConsulClient.SERVICE_ID)
public class ConsulAslTokenFilter implements HttpClientFilter, Toggleable {

    public static final String HEADER_CONSUL_TOKEN = "X-Consul-Token";

    private final ConsulConfiguration configuration;

    public ConsulAslTokenFilter(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isEnabled() {
        return configuration.getAslToken().isPresent();
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Optional<String> aslToken = configuration.getAslToken();
        aslToken.ifPresent(token -> request.header(HEADER_CONSUL_TOKEN, token));
        return chain.proceed(request);
    }
}
