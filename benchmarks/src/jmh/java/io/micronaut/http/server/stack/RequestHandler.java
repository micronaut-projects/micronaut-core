package io.micronaut.http.server.stack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

@ChannelHandler.Sharable
final class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectReader reader = objectMapper.readerFor(SearchController.Input.class);
    private final ObjectWriter writerResult = objectMapper.writerFor(SearchController.Result.class);
    private final ObjectWriter writerStatus = objectMapper.writerFor(Status.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        FullHttpResponse response = computeResponse(ctx, msg);
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response, ctx.voidPromise());
        ctx.read();
    }

    private FullHttpResponse computeResponse(ChannelHandlerContext ctx, FullHttpRequest msg) {
        try {
            String path = URI.create(msg.uri()).getPath();
            if (path.equals("/search/find")) {
                return computeResponseSearch(ctx, msg);
            }
            if (path.equals("/status")) {
                return computeResponseStatus(ctx, msg);
            }
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private FullHttpResponse computeResponseSearch(ChannelHandlerContext ctx, FullHttpRequest msg) throws IOException {
        if (!msg.method().equals(HttpMethod.POST)) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
        }
        if (!msg.headers().contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON, true)) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        ByteBuf content = msg.content();
        SearchController.Input input;
        if (content.hasArray()) {
            input = reader.readValue(content.array(), content.readerIndex() + content.arrayOffset(), content.readableBytes());
        } else {
            input = reader.readValue((InputStream) new ByteBufInputStream(content));
        }

        SearchController.Result result = find(input.haystack(), input.needle());
        if (result == null) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        } else {
            return serialize(ctx, writerResult, result);
        }
    }

    private FullHttpResponse serialize(ChannelHandlerContext ctx, ObjectWriter writer, Object result) throws IOException {
        ByteBuf buffer = ctx.alloc().buffer();
        writer.writeValue((OutputStream) new ByteBufOutputStream(buffer), result);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        return response;
    }

    private FullHttpResponse computeResponseStatus(ChannelHandlerContext ctx, FullHttpRequest msg) throws IOException {
        if (!msg.method().equals(HttpMethod.GET)) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
        }

        Status status = new Status(
            ctx.channel().getClass().getName(),
            SslContext.defaultServerProvider()
        );

        return serialize(ctx, writerStatus, status);
    }

    private static SearchController.Result find(List<String> haystack, String needle) {
        for (int listIndex = 0; listIndex < haystack.size(); listIndex++) {
            String s = haystack.get(listIndex);
            int stringIndex = s.indexOf(needle);
            if (stringIndex != -1) {
                return new SearchController.Result(listIndex, stringIndex);
            }
        }
        return null;
    }

    record Status(String channelImplementation,
                  SslProvider sslProvider) {
    }
}
