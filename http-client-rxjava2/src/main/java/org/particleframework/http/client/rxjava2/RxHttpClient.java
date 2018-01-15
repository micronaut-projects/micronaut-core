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
package org.particleframework.http.client.rxjava2;

import io.reactivex.Flowable;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.client.DefaultHttpClient;
import org.particleframework.http.client.HttpClientConfiguration;
import org.particleframework.http.client.ServerSelector;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.sse.Event;

import javax.inject.Inject;
import java.net.URL;
import java.util.Map;

/**
 * Subclass of {@link DefaultHttpClient} that exposes a RxJava 2.x API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
@Replaces(DefaultHttpClient.class)
public class RxHttpClient extends DefaultHttpClient {
    @Inject
    public RxHttpClient(@Argument URL url, HttpClientConfiguration configuration, MediaTypeCodecRegistry codecRegistry) {
        super(url, configuration, codecRegistry);
    }

    public RxHttpClient(ServerSelector serverSelector, HttpClientConfiguration configuration, MediaTypeCodecRegistry codecRegistry) {
        super(serverSelector, configuration, codecRegistry);
    }

    public RxHttpClient(ServerSelector serverSelector) {
        super(serverSelector);
    }

    public RxHttpClient(URL url) {
        super(url);
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.exchange(request));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(super.exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.retrieve(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(super.retrieve(request, bodyType));
    }

    @Override
    public <I> Flowable<HttpResponse<Event<ByteBuffer<?>>>> eventStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.eventStream(request));
    }

    @Override
    public <I, O> Flowable<HttpResponse<Event<O>>> eventStream(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.eventStream(request, bodyType));
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer<?>>> dataStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.dataStream(request));
    }

    @Override
    public <I> Flowable<HttpResponse<Map<String, Object>>> jsonStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(super.jsonStream(request));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> jsonStream(HttpRequest<I> request, org.particleframework.core.type.Argument<O> bodyType) {
        return Flowable.fromPublisher(super.jsonStream(request, bodyType));
    }
}
