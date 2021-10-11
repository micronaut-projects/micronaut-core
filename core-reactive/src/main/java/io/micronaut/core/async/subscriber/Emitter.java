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
package io.micronaut.core.async.subscriber;

/**
 * Base interface for classes that emit data. See {@link org.reactivestreams.Subscriber}.
 *
 * @param <T> type of element
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Emitter<T> extends Completable {
    /**
     * Data notification sent by the {@link org.reactivestreams.Publisher} in response to requests to {@link org.reactivestreams.Subscription#request(long)}.
     *
     * @param t the element signaled
     */
    void onNext(T t);

    /**
     * Failed terminal state.
     * <p>
     * No further events will be sent even if {@link org.reactivestreams.Subscription#request(long)} is invoked again.
     *
     * @param t the throwable signaled
     */
    void onError(Throwable t);
}
