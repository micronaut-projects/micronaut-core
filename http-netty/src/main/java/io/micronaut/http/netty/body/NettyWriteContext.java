package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpResponse;

import java.io.RandomAccessFile;

@Internal
@Experimental
public interface NettyWriteContext {
    /**
     * @return The bytebuf allocator.
     */
    ByteBufAllocator alloc();

    /**
     * Write a full response.
     *
     * @param response The response to write
     */
    void writeFull(FullHttpResponse response);

    /**
     * Write a streamed response. The actual response will only be written when the first item
     * of the {@link org.reactivestreams.Publisher} is received, in order to handle errors.
     *
     * @param response The response to write
     */
    void writeStreamed(StreamedHttpResponse response);

    /**
     * Write a response with a {@link HttpChunkedInput} body.
     *
     * @param response     The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param chunkedInput The response body
     */
    void writeChunked(HttpResponse response, HttpChunkedInput chunkedInput);

    /**
     * Write a response with a body that is a section of a {@link RandomAccessFile}.
     *
     * @param response         The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param randomAccessFile File to read from
     * @param position         Start position
     * @param contentLength    Length of the section to send
     */
    void writeFile(HttpResponse response, RandomAccessFile randomAccessFile, long position, long contentLength);
}
