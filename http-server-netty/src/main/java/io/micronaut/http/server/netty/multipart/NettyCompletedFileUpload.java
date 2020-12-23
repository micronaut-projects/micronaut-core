/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A Netty implementation of {@link CompletedFileUpload}.
 *
 * @author Zachary Klein
 * @since 1.0.0
 */
@Internal
public class NettyCompletedFileUpload implements CompletedFileUpload {

    private final FileUpload fileUpload;
    private final boolean controlRelease;

    /**
     * @param fileUpload The file upload
     */
    public NettyCompletedFileUpload(FileUpload fileUpload) {
        this(fileUpload, true);
    }

    /**
     * @param fileUpload The file upload
     * @param controlRelease If true, release after retrieving the data
     */
    public NettyCompletedFileUpload(FileUpload fileUpload, boolean controlRelease) {
        this.fileUpload = fileUpload;
        this.controlRelease = controlRelease;
        if (controlRelease) {
            fileUpload.retain();
        }
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
        if (fileUpload.isInMemory()) {
            ByteBuf byteBuf = fileUpload.getByteBuf();
            if (byteBuf == null) {
                throw new IOException("The input stream has already been released");
            }
            return new ByteBufInputStream(byteBuf, controlRelease);
        } else {
            File file = fileUpload.getFile();
            if (file == null) {
                throw new IOException("The input stream has already been released");
            }
            return new NettyFileUploadInputStream(fileUpload, controlRelease);
        }
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
        if (byteBuf == null) {
            throw new IOException("The bytes have already been released");
        }
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            if (controlRelease) {
                byteBuf.release();
            }
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
        if (byteBuf == null) {
            throw new IOException("The byte buffer has already been released");
        }
        try {
            return byteBuf.nioBuffer();
        } finally {
            if (controlRelease) {
                byteBuf.release();
            }
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
        return fileUpload.length();
    }

    @Override
    public long getDefinedSize() {
        return fileUpload.definedLength();
    }

    @Override
    public boolean isComplete() {
        return fileUpload.isCompleted();
    }
}
