/*
 * Copyright 2018 original authors
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
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * @author Zachary Klein
 * @since 1.0
 */
public class CompletedFileUpload implements io.micronaut.http.multipart.FileUpload {

    private final FileUpload fileUpload;

    public CompletedFileUpload(io.netty.handler.codec.http.multipart.FileUpload fileUpload) {
        this.fileUpload = fileUpload;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream((fileUpload.getByteBuf()));
    }

    @Override
    public byte[] getBytes() throws IOException {
        return fileUpload.getByteBuf().array();
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        return fileUpload.getByteBuf().nioBuffer();
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
