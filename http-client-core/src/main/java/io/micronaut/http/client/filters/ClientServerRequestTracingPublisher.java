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
package io.micronaut.http.client.filters;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.context.ServerRequestContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Internal tracing publisher.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class ClientServerRequestTracingPublisher implements Publisher<HttpResponse<?>> {

    private final HttpRequest<?> request;
    private final Publisher<? extends HttpResponse<?>> actual;

    /**
     * Creates a new instance.
     *
     * @param request The request
     * @param actual The target publisher
     */
    public ClientServerRequestTracingPublisher(HttpRequest<?> request, Publisher<? extends HttpResponse<?>> actual) {
        this.request = request;
        this.actual = actual;
    }

    @Override
    public void subscribe(Subscriber<? super HttpResponse<?>> subscriber) {
        ServerRequestContext.with(request, () -> actual.subscribe(new Subscriber<HttpResponse<?>>() {
            @Override
            public void onSubscribe(Subscription s) {
                ServerRequestContext.with(request, () -> subscriber.onSubscribe(s));
            }

            @Override
            public void onNext(HttpResponse<?> mutableHttpResponse) {
                ServerRequestContext.with(request, () -> subscriber.onNext(mutableHttpResponse));
            }

            @Override
            public void onError(Throwable t) {
                ServerRequestContext.with(request, () -> subscriber.onError(t));
            }

            @Override
            public void onComplete() {
                ServerRequestContext.with(request, subscriber::onComplete);
            }
        }));
    }
}
