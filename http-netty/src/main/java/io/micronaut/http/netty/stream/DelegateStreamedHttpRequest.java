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
package io.micronaut.http.netty.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.reactive.HotObservable;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Delegate for Streamed HTTP Request.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class DelegateStreamedHttpRequest extends DelegateHttpRequest implements StreamedHttpRequest {

    private final Publisher<? extends HttpContent> stream;
    private boolean consumed;

    /**
     * @param request The Http request
     * @param stream  The publisher
     */
    DelegateStreamedHttpRequest(HttpRequest request, Publisher<? extends HttpContent> stream) {
        super(request);
        this.stream = stream;
    }

    @Override
    public boolean isConsumed() {
        return this.consumed;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        consumed = true;
        stream.subscribe(subscriber);
    }

    @Override
    public void closeIfNoSubscriber() {
        if (stream instanceof HotObservable) {
            ((HotObservable<HttpContent>) stream).closeIfNoSubscriber();
        }
    }
}
