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

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
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
class EmptyHttpResponse extends DelegateHttpResponse implements FullHttpResponse {

    /**
     * @param response The Http response
     */
    EmptyHttpResponse(HttpResponse response) {
        super(response);
    }

    @Override
    public FullHttpResponse setStatus(HttpResponseStatus status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public FullHttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public FullHttpResponse copy() {
        if (response instanceof FullHttpResponse) {
            return new EmptyHttpResponse(((FullHttpResponse) response).copy());
        } else {
            DefaultHttpResponse copy = new DefaultHttpResponse(protocolVersion(), status());
            copy.headers().set(headers());
            return new EmptyHttpResponse(copy);
        }
    }

    @Override
    public FullHttpResponse retain(int increment) {
        ReferenceCountUtil.retain(message, increment);
        return this;
    }

    @Override
    public FullHttpResponse retain() {
        ReferenceCountUtil.retain(message);
        return this;
    }

    @Override
    public FullHttpResponse touch() {
        if (response instanceof FullHttpResponse) {
            return ((FullHttpResponse) response).touch();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpResponse touch(Object o) {
        if (response instanceof FullHttpResponse) {
            return ((FullHttpResponse) response).touch(o);
        } else {
            return this;
        }
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return new DefaultHttpHeaders();
    }

    @Override
    public FullHttpResponse duplicate() {
        if (response instanceof FullHttpResponse) {
            return ((FullHttpResponse) response).duplicate();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpResponse retainedDuplicate() {
        if (response instanceof FullHttpResponse) {
            return ((FullHttpResponse) response).retainedDuplicate();
        } else {
            return this;
        }
    }

    @Override
    public FullHttpResponse replace(ByteBuf byteBuf) {
        if (response instanceof FullHttpResponse) {
            return ((FullHttpResponse) response).replace(byteBuf);
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
