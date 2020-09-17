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
package io.micronaut.http.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A reactive streams publisher that instruments an existing publisher ensuring execution is
 * wrapped in a {@link ServerRequestContext}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class ServerRequestTracingPublisher implements Publishers.MicronautPublisher<MutableHttpResponse<?>> {

    private final HttpRequest<?> request;
    private final Publisher<MutableHttpResponse<?>> actual;

    /**
     * Creates a new instance.
     *
     * @param request The request
     * @param actual The target publisher
     */
    public ServerRequestTracingPublisher(HttpRequest<?> request, Publisher<MutableHttpResponse<?>> actual) {
        this.request = request;
        this.actual = actual;
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(Subscriber<? super MutableHttpResponse<?>> subscriber) {
        ServerRequestContext.with(request, () -> actual.subscribe(new Subscriber<MutableHttpResponse<?>>() {
            @Override
            public void onSubscribe(Subscription s) {
                ServerRequestContext.with(request, () -> subscriber.onSubscribe(s));
            }

            @Override
            public void onNext(MutableHttpResponse<?> mutableHttpResponse) {
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
