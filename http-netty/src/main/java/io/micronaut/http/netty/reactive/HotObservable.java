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

import org.reactivestreams.Publisher;

/**
 * A contract for a publisher that buffers data to allow for
 * the release of that data if there will not be a subscriber.
 *
 * @param <T> The type of data being published
 * @author James Kleeh
 * @since 1.2.1
 */
public interface HotObservable<T> extends Publisher<T> {

    /**
     * Releases buffered data if there is no subscriber.
     */
    void closeIfNoSubscriber();
}
