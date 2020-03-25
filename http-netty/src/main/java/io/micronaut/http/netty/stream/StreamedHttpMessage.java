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
package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import org.reactivestreams.Publisher;

/**
 * Combines {@link HttpMessage} and {@link Publisher} into one
 * message. So it represents an http message with a stream of {@link HttpContent}
 * messages that can be subscribed to.
 *
 * Note that receivers of this message <em>must</em> consume the publisher,
 * since the publisher will exert back pressure up the stream if not consumed.
 *
 * @author jroper
 * @author Graeme Rocher
 */
public interface StreamedHttpMessage extends HttpMessage, Publisher<HttpContent> {
}
