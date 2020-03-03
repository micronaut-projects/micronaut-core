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
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedPart;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A delegation of the Netty {@link Attribute} to implement
 * the {@link CompletedPart} contract.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
class NettyCompletedAttribute implements CompletedPart {

    private final Attribute attribute;
    private final boolean controlRelease;

    /**
     * @param attribute The netty attribute
     */
    public NettyCompletedAttribute(Attribute attribute) {
        this(attribute, true);
    }

    /**
     * @param attribute The netty attribute
     * @param controlRelease If true, release after retrieving the attribute data
     */
    NettyCompletedAttribute(Attribute attribute, boolean controlRelease) {
        this.attribute = attribute;
        this.controlRelease = controlRelease;
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(attribute.getByteBuf(), controlRelease);
    }

    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = attribute.getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            if (controlRelease) {
                attribute.release();
            }
        }
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = attribute.getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            if (controlRelease) {
                attribute.release();
            }
        }
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.empty();
    }
}
