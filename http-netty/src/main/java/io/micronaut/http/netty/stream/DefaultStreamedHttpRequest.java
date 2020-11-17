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
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * A default streamed HTTP request.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultStreamedHttpRequest extends DefaultHttpRequest implements StreamedHttpRequest {

    private final Publisher<HttpContent> stream;
    private boolean consumed;

    /**
     * @param httpVersion The Http Version
     * @param method      The Http Method
     * @param uri         The URI
     * @param stream      The publisher
     */
    public DefaultStreamedHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, Publisher<HttpContent> stream) {
        super(httpVersion, method, uri);
        this.stream = stream;
    }

    @Override
    public boolean isConsumed() {
        return this.consumed;
    }

    /**
     * @param httpVersion     The Http Version
     * @param method          The Http Method
     * @param uri             The URI
     * @param validateHeaders Whether to validate the headers
     * @param stream          The publisher
     */
    public DefaultStreamedHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean validateHeaders, Publisher<HttpContent> stream) {
        super(httpVersion, method, uri, validateHeaders);
        this.stream = stream;
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
