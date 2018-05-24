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
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A Netty implementation of {@link CompletedFileUpload}.
 *
 * @author Zachary Klein
 * @since 1.0
 */
public class NettyCompletedFileUpload implements CompletedFileUpload {

    private final FileUpload fileUpload;

    /**
     * @param fileUpload The file upload
     */
    public NettyCompletedFileUpload(FileUpload fileUpload) {
        this.fileUpload = fileUpload;
        fileUpload.retain();
    }

    /**
     * Gets the content of this part as a <tt>InputStream</tt>.
     *
     * <p>The contents of the file will be released when the stream is closed.
     * This method should only be called <strong>once</strong></p>
     *
     * @return The content of this part as a <tt>InputStream</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(fileUpload.getByteBuf(), true);
    }

    /**
     * Gets the content of this part as a <tt>byte[]</tt>.
     *
     * <p>Because the contents of the file are released after being retrieved,
     * this method can only be called <strong>once</strong></p>
     *
     * @return The content of this part as a <tt>byte[]</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = fileUpload.getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    /**
     * Gets the content of this part as a <tt>ByteBuffer</tt>.
     *
     * <p>Because the contents of the file are released after being retrieved,
     * this method can only be called <strong>once</strong></p>
     *
     * @return The content of this part as a <tt>ByteBuffer</tt>
     * @throws IOException If an error occurs in retrieving the content
     */
    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = fileUpload.getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(MediaType.of(fileUpload.getContentType()));
    }

    @Override
    public String getName() {
        return fileUpload.getName();
    }

    @Override
    public String getFilename() {
        return fileUpload.getFilename();
    }

    @Override
    public long getSize() {
        return fileUpload.definedLength();
    }

    @Override
    public boolean isComplete() {
        return fileUpload.isCompleted();
    }
}
