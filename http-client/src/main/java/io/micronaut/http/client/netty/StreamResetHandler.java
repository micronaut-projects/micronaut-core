/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2ResetFrame;

/**
 * Reports a {@code RST_STREAM} of the server as an exception of the request on an HTTP/2 stream
 * channel, before the channel closes: a refused stream was not processed, so the request can be
 * sent again; any other reset may have been processed. The multiplex handler delivers reset
 * frames as user events, since they are not flow controlled.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class StreamResetHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof Http2ResetFrame reset) {
            long errorCode = reset.errorCode();
            HttpClientException failure;
            if (errorCode == Http2Error.REFUSED_STREAM.code()) {
                failure = new UnprocessedRequestException(UnprocessedRequestException.Reason.STREAM_REFUSED, "Stream refused by the server (REFUSED_STREAM)", null);
            } else {
                failure = new StreamResetException(errorCode);
            }
            ctx.fireExceptionCaught(failure);
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }
}
