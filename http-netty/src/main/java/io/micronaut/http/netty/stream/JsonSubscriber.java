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
import io.micronaut.http.netty.content.HttpContentUtil;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
@Internal
public final class JsonSubscriber implements Subscriber<HttpContent> {

    private final AtomicBoolean empty = new AtomicBoolean(true);
    private final Subscriber<? super HttpContent> upstream;

    public JsonSubscriber(Subscriber<? super HttpContent> upstream) {
        this.upstream = upstream;
    }

    @Override
    public void onSubscribe(Subscription s) {
        upstream.onSubscribe(s);
    }

    /**
     * The goal is to prevent the emission of the
     * opening bracket if the underlying stream never emits
     * an item and only produces an error.
     *
     * @param o The content
     */
    @Override
    public void onNext(HttpContent o) {
        if (empty.compareAndSet(true, false)) {
            upstream.onNext(HttpContentUtil.prefixOpenBracket(o));
        } else {
            upstream.onNext(HttpContentUtil.prefixComma(o));
        }
    }

    @Override
    public void onError(Throwable t) {
        upstream.onError(t);
    }

    /**
     * On complete the opening bracket should be emitted
     * if no items were ever produced, then the closing bracket.
     */
    @Override
    public void onComplete() {
        if (empty.get()) {
            upstream.onNext(HttpContentUtil.prefixOpenBracket(HttpContentUtil.closeBracket()));
        } else {
            upstream.onNext(HttpContentUtil.closeBracket());
        }
        upstream.onComplete();
    }
}
