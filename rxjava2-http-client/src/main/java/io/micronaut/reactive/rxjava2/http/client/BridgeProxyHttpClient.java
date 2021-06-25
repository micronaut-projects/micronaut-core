/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.reactive.rxjava2.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.ProxyHttpClient;
import io.reactivex.Flowable;


/**
 * Internal bridge for the {@link ProxyHttpClient} client.
 *
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Internal
class BridgeProxyHttpClient implements RxProxyHttpClient {

    private final ProxyHttpClient proxyHttpClient;

    BridgeProxyHttpClient(ProxyHttpClient proxyHttpClient) {
        this.proxyHttpClient = proxyHttpClient;
    }

    @Override
    public Flowable<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
        return Flowable.fromPublisher(proxyHttpClient.proxy(request));
    }
}
