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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

/**
 * Delegate for Empty HTTP Response.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class EmptyHttpRequest extends DelegateHttpRequest implements FullHttpRequest {

    /**
     * @param request The Http request
     */
    EmptyHttpRequest(HttpRequest request) {
        super(request);
    }

    @Override
    public FullHttpRequest setUri(String uri) {
        super.setUri(uri);
        return this;
    }

    @Override
    public FullHttpRequest setMethod(HttpMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public FullHttpRequest setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public FullHttpRequest copy() {
        if (request instanceof FullHttpRequest) {
            return new EmptyHttpRequest(((FullHttpRequest) request).copy());
        } else {
            DefaultHttpRequest copy = new DefaultHttpRequest(protocolVersion(), method(), uri());
            copy.headers().set(headers());
            return new EmptyHttpRequest(copy);
        }
    }

    @Override
    public FullHttpRequest retain(int increment) {
        ReferenceCountUtil.retain(message, increment);
        return this;
    }

    @Override
    public FullHttpRequest retain() {
        ReferenceCountUtil.retain(message);
        return this;
    }

    @Override
    public FullHttpRequest touch() {
        if (request instanceof FullHttpRequest) {
            return ((FullHttpRequest) request).touch();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpRequest touch(Object o) {
        if (request instanceof FullHttpRequest) {
            return ((FullHttpRequest) request).touch(o);
        } else {
            return this;
        }
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return new DefaultHttpHeaders();
    }

    @Override
    public FullHttpRequest duplicate() {
        if (request instanceof FullHttpRequest) {
            return ((FullHttpRequest) request).duplicate();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpRequest retainedDuplicate() {
        if (request instanceof FullHttpRequest) {
            return ((FullHttpRequest) request).retainedDuplicate();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpRequest replace(ByteBuf byteBuf) {
        if (message instanceof FullHttpRequest) {
            return ((FullHttpRequest) request).replace(byteBuf);
        } else {
            return this;
        }
    }

    @Override
    public ByteBuf content() {
        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public int refCnt() {
        if (message instanceof ReferenceCounted) {
            return ((ReferenceCounted) message).refCnt();
        } else {
            return 1;
        }
    }

    @Override
    public boolean release() {
        return ReferenceCountUtil.release(message);
    }

    @Override
    public boolean release(int decrement) {
        return ReferenceCountUtil.release(message, decrement);
    }
}
