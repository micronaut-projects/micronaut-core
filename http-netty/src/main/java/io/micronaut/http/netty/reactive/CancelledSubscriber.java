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
package io.micronaut.http.netty.reactive;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A cancelled subscriber.
 *
 * @param <T> The subscriber type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class CancelledSubscriber<T> implements Subscriber<T> {

    @Override
    public void onSubscribe(Subscription subscription) {
        if (subscription == null) {
            throw new NullPointerException("Null subscription");
        } else {
            subscription.cancel();
        }
    }

    @Override
    public void onNext(T t) {
    }

    @Override
    public void onError(Throwable error) {
        if (error == null) {
            throw new NullPointerException("Null error published");
        }
    }

    @Override
    public void onComplete() {
    }
}
