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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.RxHttpClientFactory;
import io.micronaut.http.client.RxStreamingHttpClient;

import java.net.URL;

/**
 * Implementation of {@link RxHttpClientFactory} for Netty.
 *
 * @author graemerocher
 * @since 2.0
 */
@Internal
public class NettyRxHttpClientFactory implements RxHttpClientFactory {
    @Override
    public RxHttpClient createClient(URL url) {
        return new DefaultHttpClient(url);
    }

    @Override
    public RxStreamingHttpClient createStreamingClient(URL url) {
        return new DefaultHttpClient(url);
    }
}
