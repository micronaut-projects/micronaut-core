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
package org.particleframework.http;

import java.util.Map;
import java.util.function.Consumer;

/**
 * An extended version of {@link HttpRequest} that allows mutating headers, the body etc.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableHttpRequest<B> extends HttpRequest<B>, MutableHttpMessage<B> {
    @Override
    MutableHttpRequest<B> body(B body);

    @Override
    default MutableHttpRequest<B> headers(Consumer<MutableHttpHeaders> headers) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.headers(headers);
    }

    @Override
    default MutableHttpRequest<B> header(CharSequence name, CharSequence value) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.header(name, value);
    }

    @Override
    default MutableHttpRequest<B> headers(Map<CharSequence, CharSequence> namesAndValues) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.headers(namesAndValues);
    }

    @Override
    default MutableHttpRequest<B> contentLength(long length) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.contentLength(length);
    }

    @Override
    default MutableHttpRequest<B> contentType(CharSequence contentType) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.contentType(contentType);
    }

    @Override
    default MutableHttpRequest<B> contentType(MediaType mediaType) {
        return (MutableHttpRequest<B>) MutableHttpMessage.super.contentType(mediaType);
    }
}
