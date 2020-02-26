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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.functional.ThrowingSupplier;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.server.netty.HttpDataReference;
import io.netty.buffer.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A Netty implementation of {@link PartData}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class NettyPartData implements PartData {

    private final Supplier<Optional<MediaType>> mediaTypeSupplier;
    private final ThrowingSupplier<ByteBuf, IOException> byteBufSupplier;

    /**
     * @param httpData   The data reference
     * @param component  The component reference
     */
    public NettyPartData(HttpDataReference httpData, HttpDataReference.Component component) {
        this(httpData::getContentType, component::getByteBuf);
    }

    /**
     * @param mediaTypeSupplier The content type supplier
     * @param byteBufSupplier   The byte buffer supplier
     */
    public NettyPartData(Supplier<Optional<MediaType>> mediaTypeSupplier, ThrowingSupplier<ByteBuf, IOException> byteBufSupplier) {
        this.mediaTypeSupplier = mediaTypeSupplier;
        this.byteBufSupplier = byteBufSupplier;
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
        return mediaTypeSupplier.get();
    }

    /**
     * @return The native netty {@link ByteBuf} for this chunk
     * @throws IOException If an error occurs retrieving the buffer
     */
    public ByteBuf getByteBuf() throws IOException {
        return byteBufSupplier.get();
    }
}
