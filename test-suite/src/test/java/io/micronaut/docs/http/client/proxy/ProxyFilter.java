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
package io.micronaut.docs.http.client.proxy;

// tag::imports[]
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.filter.*;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.runtime.server.EmbeddedServer;
import org.reactivestreams.Publisher;
// end::imports[]

// tag::class[]
@Filter("/proxy/**")
public class ProxyFilter extends OncePerRequestHttpServerFilter { // <1>
    private final ProxyHttpClient client;
    private final EmbeddedServer embeddedServer;

    public ProxyFilter(ProxyHttpClient client, EmbeddedServer embeddedServer) { // <2>
        this.client = client;
        this.embeddedServer = embeddedServer;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(client.proxy( // <3>
                request.mutate() // <4>
                        .uri(b -> b // <5>
                                .scheme("http")
                                .host(embeddedServer.getHost())
                                .port(embeddedServer.getPort())
                                .replacePath(StringUtils.prependUri(
                                        "/real",
                                        request.getPath().substring("/proxy".length())
                                ))
                        )
                        .header("X-My-Request-Header", "XXX") // <6>
        ), response -> response.header("X-My-Response-Header", "YYY"));
    }
}
// end::class[]
