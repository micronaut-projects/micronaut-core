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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Delegate for HTTP Message.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DelegateHttpMessage implements HttpMessage {

    protected final HttpMessage message;

    /**
     * @param message The message
     */
    DelegateHttpMessage(HttpMessage message) {
        this.message = message;
    }

    @Override
    @Deprecated
    public HttpVersion getProtocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public HttpMessage setProtocolVersion(HttpVersion version) {
        message.setProtocolVersion(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return message.headers();
    }

    @Override
    @Deprecated
    public DecoderResult getDecoderResult() {
        return message.decoderResult();
    }

    @Override
    public DecoderResult decoderResult() {
        return message.decoderResult();
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        message.setDecoderResult(result);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "(" + message.toString() + ")";
    }
}
