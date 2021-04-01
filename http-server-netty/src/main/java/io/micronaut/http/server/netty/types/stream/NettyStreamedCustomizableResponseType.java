package io.micronaut.http.server.netty.types.stream;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseType;
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

public interface NettyStreamedCustomizableResponseType extends NettyCustomizableResponseType {

    Logger LOG = LoggerFactory.getLogger(NettyStreamedCustomizableResponseType.class);

    InputStream getInputStream();

    Executor getExecutor();

    @Override
    default void write(HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {
        if (response instanceof NettyMutableHttpResponse) {
            FullHttpResponse nettyResponse = ((NettyMutableHttpResponse) response).getNativeResponse();

            // Write the request data
            final DefaultHttpResponse finalResponse = new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers());
            final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
            final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;
            if (isHttp2 && request instanceof NettyHttpRequest) {
                final io.netty.handler.codec.http.HttpHeaders nativeHeaders = ((NettyHttpRequest<?>) request).getNativeRequest().headers();
                final String streamId = nativeHeaders.get(AbstractNettyHttpRequest.STREAM_ID);
                if (streamId != null) {
                    finalResponse.headers().set(AbstractNettyHttpRequest.STREAM_ID, streamId);
                }
            }
            InputStream inputStream = getInputStream();
            //  can be null if the stream was closed
            context.write(finalResponse, context.voidPromise());

            if (inputStream != null) {
                ChannelFutureListener closeListener = (future) -> {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOG.warn("An error occurred closing an input stream", e);
                    }
                };
                final HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedStream(inputStream));
                if (context.executor().inEventLoop()) {
                    getExecutor().execute(() -> {
                        context.writeAndFlush(chunkedInput).addListener(closeListener);
                    });
                } else {
                    context.writeAndFlush(chunkedInput).addListener(closeListener);
                }
            } else {
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            }

        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }

    @Override
    default void process(MutableHttpResponse<?> response) {
        response.header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
    }
}
