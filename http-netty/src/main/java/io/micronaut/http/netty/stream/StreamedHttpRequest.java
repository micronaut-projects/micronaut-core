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

import io.netty.handler.codec.http.HttpRequest;

/**
 * Combines {@link HttpRequest} and {@link StreamedHttpMessage} into one
 * message. So it represents an http request with a stream of
 * {@link io.netty.handler.codec.http.HttpContent} messages that can be subscribed to.
 *
 * @author jroper
 * @author Graeme Rocher
 */
public interface StreamedHttpRequest extends HttpRequest, StreamedHttpMessage {

    /**
     * Releases the stream if there is no subscriber.
     */
    default void closeIfNoSubscriber() { }

    /**
     * Has the stream been consumed.
     * @return True if it has
     */
    default boolean isConsumed() {
        return false;
    }
}
