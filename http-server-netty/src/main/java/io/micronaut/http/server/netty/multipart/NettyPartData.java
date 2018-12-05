/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.netty.buffer.*;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Optional;

/**
 * A Netty implementation of {@link PartData}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class NettyPartData implements PartData {

    private static final Logger LOG = LoggerFactory.getLogger(NettyPartData.class);

    private final FileUpload fileUpload;
    private final long start;
    private final int length;
    private final FileChannel channel;

    /**
     * @param fileUpload The file upload
     * @param start      The index where to start reading bytes
     * @param length     The number of bytes to read
     */
    public NettyPartData(FileUpload fileUpload, long start, int length, @Nullable FileChannel channel) {
        this.fileUpload = fileUpload;
        this.start = start;
        this.length = length;
        this.channel = channel;
        if (!fileUpload.isInMemory() && channel == null) {
            throw new IllegalArgumentException("Creating a NettyPartData with a disk file upload without a channel is not allowed.");
        }
    }

    /**
     * The contents of the chunk will be released when the stream is closed.
     *
     * @see PartData#getInputStream()
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(getByteBuf(), true);
    }

    /**
     * The contents of the chunk are released immediately.
     *
     * @see PartData#getBytes()
     */
    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    /**
     * The contents of the chunk are released immediately.
     *
     * @see PartData#getByteBuffer()
     */
    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            byteBuf.release();
        }
    }

    /**
     * @see PartData#getContentType()
     */
    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(MediaType.of(fileUpload.getContentType()));
    }

    /**
     * @return The native netty {@link ByteBuf} for this chunk
     * @throws IOException If an error occurs retrieving the buffer
     */
    public ByteBuf getByteBuf() throws IOException {
        if (fileUpload.isInMemory()) {
            return fileUpload.getByteBuf().retainedSlice((int)start, length);
        } else {
            byte[] data = new byte[length];
            channel.read(ByteBuffer.wrap(data), start);
            try {
                if (start + length == fileUpload.definedLength()) {
                    channel.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing file channel for file upload", e);
            }
            return Unpooled.wrappedBuffer(data);
        }
    }
}
