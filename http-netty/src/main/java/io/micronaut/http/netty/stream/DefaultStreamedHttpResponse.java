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
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * A default streamed HTTP response.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultStreamedHttpResponse extends DefaultHttpResponse implements StreamedHttpResponse {

    private final Publisher<HttpContent> stream;

    /**
     * @param version The Http Version
     * @param status  The Http response status
     * @param stream  The publisher
     */
    public DefaultStreamedHttpResponse(HttpVersion version, HttpResponseStatus status, Publisher<HttpContent> stream) {
        super(version, status);
        this.stream = stream;
    }

    /**
     * @param version         The Http Version
     * @param status          The Http response status
     * @param validateHeaders Whether to validate the headers
     * @param stream          The publisher
     */
    public DefaultStreamedHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders, Publisher<HttpContent> stream) {
        super(version, status, validateHeaders);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }

}
