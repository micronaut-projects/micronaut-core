/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.netty;

import io.micronaut.core.util.SupplierUtil;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.AbstractHttpData;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryFileUpload;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Supplier;

/**
 * Copied and modified from {@link io.netty.handler.codec.http.multipart.MixedFileUpload}, pending
 * fix for https://github.com/netty/netty/issues/12627.
 */
final class MixedFileUploadPatched extends AbstractHttpData implements FileUpload {
    private static final Supplier<ResourceLeakDetector<MixedFileUploadPatched>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
        ResourceLeakDetectorFactory.instance().newResourceLeakDetector(MixedFileUploadPatched.class));

    private final String baseDir;

    private final boolean deleteOnExit;

    private FileUpload fileUpload;

    private final long limitSize;

    private final long definedSize;

    private final ResourceLeakTracker<MixedFileUploadPatched> tracker;

    public MixedFileUploadPatched(String name, String filename, String contentType,
                           String contentTransferEncoding, Charset charset, long size,
                           long limitSize, String baseDir, boolean deleteOnExit) {
        super(name, charset, size);
        this.limitSize = limitSize;
        if (size > this.limitSize) {
            fileUpload = new DiskFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
        } else {
            fileUpload = new MemoryFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
        }
        tracker = LEAK_DETECTOR.get().track(this);
        definedSize = size;
        this.baseDir = baseDir;
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public void setMaxSize(long maxSize) {
        super.setMaxSize(maxSize);
        fileUpload.setMaxSize(maxSize);
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last)
        throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            try {
                checkSize(fileUpload.length() + buffer.readableBytes());
                if (fileUpload.length() + buffer.readableBytes() > limitSize) {
                    DiskFileUpload diskFileUpload = new DiskFileUpload(fileUpload
                        .getName(), fileUpload.getFilename(), fileUpload
                        .getContentType(), fileUpload
                        .getContentTransferEncoding(), fileUpload.getCharset(),
                        definedSize, baseDir, deleteOnExit);
                    diskFileUpload.setMaxSize(getMaxSize());
                    ByteBuf data = fileUpload.getByteBuf();
                    if (data != null && data.isReadable()) {
                        diskFileUpload.addContent(data.retain(), false);
                    }
                    // release old upload
                    fileUpload.release();

                    fileUpload = diskFileUpload;
                }
            } catch (IOException e) {
                buffer.release();
                throw e;
            }
        }
        fileUpload.addContent(buffer, last);
    }

    @Override
    public void delete() {
        fileUpload.delete();
        if (tracker != null) {
            tracker.close(this);
        }
    }

    @Override
    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
        return fileUpload.getByteBuf();
    }

    @Override
    public String getContentType() {
        return fileUpload.getContentType();
    }

    @Override
    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    @Override
    public String getFilename() {
        return fileUpload.getFilename();
    }

    @Override
    public String getString() throws IOException {
        return fileUpload.getString();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    @Override
    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    @Override
    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    @Override
    public long length() {
        return fileUpload.length();
    }

    @Override
    public long definedLength() {
        return fileUpload.definedLength();
    }

    @Override
    public MixedFileUploadPatched touch() {
        return touch(null);
    }

    @Override
    public MixedFileUploadPatched touch(Object hint) {
        if (tracker != null) {
            tracker.record(hint);
        }
        return this;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    @Override
    public void setCharset(Charset charset) {
        super.setCharset(charset);
        // called from the super <init>, ugly workaround
        if (fileUpload != null) {
            fileUpload.setCharset(charset);
        }
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        try {
            checkSize(buffer.readableBytes());
        } catch (IOException e) {
            buffer.release();
            throw e;
        }
        if (buffer.readableBytes() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                FileUpload memoryUpload = fileUpload;
                // change to Disk
                fileUpload = new DiskFileUpload(memoryUpload
                    .getName(), memoryUpload.getFilename(), memoryUpload
                    .getContentType(), memoryUpload
                    .getContentTransferEncoding(), memoryUpload.getCharset(),
                    definedSize, baseDir, deleteOnExit);
                fileUpload.setMaxSize(getMaxSize());

                // release old upload
                memoryUpload.release();
            }
        }
        fileUpload.setContent(buffer);
    }

    @Override
    public void setContent(File file) throws IOException {
        checkSize(file.length());
        if (file.length() > limitSize) {
            if (fileUpload instanceof MemoryFileUpload) {
                FileUpload memoryUpload = fileUpload;

                // change to Disk
                fileUpload = new DiskFileUpload(memoryUpload
                    .getName(), memoryUpload.getFilename(), memoryUpload
                    .getContentType(), memoryUpload
                    .getContentTransferEncoding(), memoryUpload.getCharset(),
                    definedSize, baseDir, deleteOnExit);
                fileUpload.setMaxSize(getMaxSize());

                // release old upload
                memoryUpload.release();
            }
        }
        fileUpload.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        if (fileUpload instanceof MemoryFileUpload) {
            FileUpload memoryUpload = fileUpload;

            // change to Disk
            fileUpload = new DiskFileUpload(fileUpload
                .getName(), fileUpload.getFilename(), fileUpload
                .getContentType(), fileUpload
                .getContentTransferEncoding(), fileUpload.getCharset(),
                definedSize, baseDir, deleteOnExit);
            fileUpload.setMaxSize(getMaxSize());

            // release old upload
            memoryUpload.release();
        }
        fileUpload.setContent(inputStream);
    }

    @Override
    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    @Override
    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    @Override
    public InterfaceHttpData.HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    @Override
    public int hashCode() {
        return fileUpload.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return fileUpload.equals(obj);
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }

    @Override
    public String toString() {
        return "Mixed: " + fileUpload;
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }

    @Override
    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

    @Override
    public FileUpload copy() {
        return fileUpload.copy();
    }

    @Override
    public FileUpload duplicate() {
        return fileUpload.duplicate();
    }

    @Override
    public FileUpload retainedDuplicate() {
        return fileUpload.retainedDuplicate();
    }

    @Override
    public FileUpload replace(ByteBuf content) {
        return fileUpload.replace(content);
    }

    @Override
    public MixedFileUploadPatched retain() {
        return (MixedFileUploadPatched) super.retain();
    }

    @Override
    public MixedFileUploadPatched retain(int increment) {
        return (MixedFileUploadPatched) super.retain(increment);
    }
}
