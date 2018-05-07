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

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A Netty implementation of {@link PartData}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class NettyPartData implements PartData {

    private final FileUpload fileUpload;
    private final Long chunk;

    /**
     * @param fileUpload The file upload
     * @param chunk      The chunk
     */
    public NettyPartData(FileUpload fileUpload, Long chunk) {
        this.fileUpload = fileUpload;
        this.chunk = chunk;
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
        return fileUpload.getChunk(chunk.intValue());
    }
}
