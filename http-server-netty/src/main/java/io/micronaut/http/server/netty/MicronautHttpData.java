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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Alternate {@link HttpData} implementation with some limited concurrency support. Only implements
 * the features we actually need.<br>
 * In most cases, we only access the {@link HttpData} on a single thread, with the standard
 * {@link #get()} and friends. However, if the user wants a reactive stream of data as it comes in,
 * this class can release chunks of that data for concurrent access by the user (see
 * {@link #pollChunk()}).<br>
 * This class moves data to disk dynamically once the configured threshold is reached.
 *
 * @param <D> This {@link HttpData} type, for {@code return (D) this} on various methods
 */
@Internal
public abstract sealed class MicronautHttpData<D extends HttpData> extends AbstractReferenceCounted implements HttpData {
    @SuppressWarnings("rawtypes")
    private static final Supplier<ResourceLeakDetector<MicronautHttpData>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
        ResourceLeakDetectorFactory.instance().newResourceLeakDetector(MicronautHttpData.class));

    private static final Logger LOG = LoggerFactory.getLogger(MicronautHttpData.class);

    private static final int MMAP_SEGMENT_SIZE = 1024 * 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = 1024 * 1024 * 1024;

    final Factory factory;

    long definedSize = 0;
    Charset charset;

    @Nullable
    @SuppressWarnings("rawtypes")
    private final ResourceLeakTracker<MicronautHttpData> tracker = LEAK_DETECTOR.get().track(this);

    private final String name;

    private final List<Chunk> chunks = new ArrayList<>();

    private long size = 0;

    @Nullable
    private Path path;
    private FileChannel channel;
    private List<ByteBuf> mmapSegments;

    private boolean completed = false;

    private int pollIndex = 0;

    private MicronautHttpData(Factory factory, String name) {
        this.factory = factory;
        this.name = name;
        this.charset = factory.characterEncoding;
        chunks.add(new Chunk(0));
    }

    private boolean shouldMoveToDisk(long newSize) {
        if (factory.multipartConfiguration.isDisk()) {
            return true;
        } else if (factory.multipartConfiguration.isMixed()) {
            return newSize >= factory.multipartConfiguration.getThreshold();
        } else {
            return false;
        }
    }

    private Chunk lastChunk() {
        return chunks.get(chunks.size() - 1);
    }

    /**
     * Get a chunk of data. The chunk will have a fixed content, it will not be amended with
     * further input.
     *
     * @return The chunk, or {@code null} if this data is {@link #isCompleted() completed} and all
     * chunks have been polled.
     */
    public Chunk pollChunk() {
        if (pollIndex >= chunks.size()) {
            return null;
        }
        Chunk chunk = chunks.get(pollIndex++);
        if (pollIndex == chunks.size() && !completed) {
            chunks.add(new Chunk(size));
        }
        // ownership of the chunk is shared: One release call from our deallocate(), one release
        // call by the caller of pollChunk(). Usually this retain corresponds to the release in
        // Chunk.claim
        chunk.retain();
        return chunk;
    }

    public InputStream toStream() {
        retain();
        return new StreamImpl();
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        if (completed) {
            throw new IllegalStateException("Already completed");
        }
        buffer.touch();
        long newSize = size + buffer.readableBytes();
        if (newSize > factory.multipartConfiguration.getMaxFileSize()) {
            buffer.release();
            throw new IOException("Size exceed allowed maximum capacity");
        }
        if (channel == null && shouldMoveToDisk(newSize)) {
            transferToDisk();
        }

        // find a chunk
        Chunk chunk;
        int newChunkSize;
        while (true) {
            chunk = lastChunk();
            if (chunk.lock.tryLock()) {
                if (chunk.buf == null) {
                    newChunkSize = buffer.readableBytes();
                } else {
                    newChunkSize = chunk.buf.readableBytes() + buffer.readableBytes();
                    if (newChunkSize > MAX_CHUNK_SIZE) {
                        newChunkSize = -1; // create new chunk
                    }
                }
                if (newChunkSize >= 0) {
                    break;
                } else {
                    // size overflow or hit limit, make a new chunk
                    chunk.lock.unlock();
                }
            }
            chunks.add(new Chunk(size));
        }
        // add to the chunk
        try {
            if (channel == null) {
                if (chunk.buf == null) {
                    chunk.buf = buffer;
                } else if (chunk.buf instanceof CompositeByteBuf composite) {
                    composite.addComponent(true, buffer);
                } else {
                    chunk.buf = Unpooled.compositeBuffer()
                        .addComponent(true, chunk.buf)
                        .addComponent(true, buffer);
                }
            } else {
                buffer.readBytes(channel, size, buffer.readableBytes());
                buffer.release();
                chunk.loadFromDisk(newChunkSize);
            }
            size = newSize;
            if (newSize > definedSize && definedSize != 0) {
                definedSize = newSize;
            }
        } finally {
            chunk.lock.unlock();
        }
        if (last) {
            completed = true;
            if (channel != null) {
                channel.close();
            }
        }
    }

    private ByteBuf mmapSegment(int index) throws IOException {
        while (mmapSegments.size() <= index) {
            mmapSegments.add(null);
        }
        ByteBuf segment = mmapSegments.get(index);
        if (segment == null) {
            segment = Unpooled.wrappedBuffer(
                channel.map(FileChannel.MapMode.READ_ONLY, (long) index * MMAP_SEGMENT_SIZE, MMAP_SEGMENT_SIZE));
            mmapSegments.set(index, segment);
        }
        return segment;
    }

    private void transferToDisk() throws IOException {
        assert channel == null;

        path = newTempFile();
        channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);

        for (Chunk chunk : chunks) {
            if (chunk.buf != null) {
                chunk.buf.getBytes(chunk.buf.readerIndex(), channel, chunk.offset, chunk.buf.readableBytes());
            }
        }
        mmapSegments = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (chunk.lock.tryLock()) {
                try {
                    if (chunk.buf != null) {
                        chunk.loadFromDisk(chunk.buf.readableBytes());
                    }
                } finally {
                    chunk.lock.unlock();
                }
            } // if tryLock failed, the user already requested the chunk, we can't move it anymore.
        }
    }

    private Path newTempFile() throws IOException {
        Optional<File> location = factory.multipartConfiguration.getLocation();
        if (location.isPresent()) {
            return Files.createTempFile(location.get().toPath(), "FUp_", ".tmp");
        } else {
            return Files.createTempFile("FUp_", ".tmp");
        }
    }

    @Override
    protected void deallocate() {
        if (tracker != null) {
            tracker.close(this);
        }
        dealloc0();
    }

    private void dealloc0() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.warn("Failed to close temp file channel", e);
            }
        }
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOG.warn("Failed to delete temp file", e);
            }
        }
        for (Chunk chunk : chunks) {
            chunk.release();
        }
        if (mmapSegments != null) {
            for (ByteBuf segment : mmapSegments) {
                segment.release();
            }
        }
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        dealloc0();
        chunks.clear();

        Chunk ch = new Chunk(0);
        chunks.add(ch);
        ch.buf = buffer;
        size = buffer.readableBytes();
    }

    @Override
    public long getMaxSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxSize(long maxSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContent(File file) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public long definedLength() {
        return definedSize;
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] get() throws IOException {
        byte[] arr = new byte[Math.toIntExact(size)];
        for (Chunk chunk : chunks) {
            if (!chunk.lock.tryLock()) {
                throw new IllegalStateException(
                    "Chunk already claimed (or get() called concurrently, which is not allowed)");
            }
            try {
                if (chunk.buf != null) {
                    chunk.buf.getBytes(chunk.buf.readerIndex(), arr, Math.toIntExact(chunk.offset), chunk.buf.readableBytes());
                }
            } finally {
                chunk.lock.unlock();
            }
        }
        return arr;
    }

    @Override
    public ByteBuf getByteBuf() {
        // todo: can't use pooled buffer here, HttpPostStandardRequestDecoder has a bug where it
        //  doesn't release the buffer properly
        ByteBuf buf = Unpooled.buffer(Math.toIntExact(size));
        for (Chunk chunk : chunks) {
            if (!chunk.lock.tryLock()) {
                buf.release();
                throw new IllegalStateException(
                    "Chunk already claimed (or get() called concurrently, which is not allowed)");
            }
            try {
                if (chunk.buf != null) {
                    chunk.buf.getBytes(chunk.buf.readerIndex(), buf, chunk.buf.readableBytes());
                }
            } finally {
                chunk.lock.unlock();
            }
        }
        return buf;
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return new String(get(), encoding);
    }

    @Override
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInMemory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getFile() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf content() {
        return getByteBuf();
    }

    @Override
    public D copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public D duplicate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public D retainedDuplicate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public D replace(ByteBuf content) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public D touch(Object hint) {
        if (tracker != null) {
            tracker.record(hint);
        }
        return (D) this;
    }

    @Override
    public int compareTo(@NonNull InterfaceHttpData o) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D retain() {
        return (D) super.retain();
    }

    @SuppressWarnings("unchecked")
    @Override
    public D retain(int increment) {
        return (D) super.retain(increment);
    }

    @SuppressWarnings("unchecked")
    @Override
    public D touch() {
        if (tracker != null) {
            tracker.record();
        }
        return (D) super.touch();
    }

    private static final class AttributeImpl extends MicronautHttpData<Attribute> implements Attribute {
        AttributeImpl(Factory factory, String name) {
            super(factory, name);
        }

        @Override
        public String getValue() throws IOException {
            return new String(get(), factory.characterEncoding);
        }

        @Override
        public void setValue(String value) throws IOException {
            setContent(Unpooled.copiedBuffer(value, factory.characterEncoding));
        }

        @Override
        public HttpDataType getHttpDataType() {
            return HttpDataType.Attribute;
        }
    }

    private static final class FileUploadImpl extends MicronautHttpData<FileUpload> implements FileUpload {
        private final String fileName;
        private final String contentType;

        FileUploadImpl(Factory factory, String name, String fileName, String contentType) {
            super(factory, name);
            this.fileName = fileName;
            this.contentType = contentType;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public void setFilename(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentType(String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void setContentTransferEncoding(String contentTransferEncoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getContentTransferEncoding() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpDataType getHttpDataType() {
            return HttpDataType.FileUpload;
        }
    }

    /**
     * Chunk of bytes from this data object. When this is exposed (returned by
     * {@link #pollChunk()}), the data is "fixed", there won't be new data added.
     */
    public final class Chunk extends AbstractReferenceCounted {
        // one reference is kept by the MicronautHttpData.chunks list, and is released on MicronautHttpData.deallocate.
        // The other reference is created by the user on pollChunk, and released when she calls claim()

        private final Lock lock = new ReentrantLock();
        private final long offset;
        @Nullable
        private ByteBuf buf; // always has refCnt = 1

        private Chunk(long offset) {
            this.offset = offset;
        }

        private void loadFromDisk(int length) throws IOException {
            int firstSegmentIndex = Math.toIntExact(offset / MMAP_SEGMENT_SIZE);
            int lastSegmentIndex = Math.toIntExact((offset + length - 1) / MMAP_SEGMENT_SIZE);

            int offsetInSegment = Math.toIntExact(offset % MMAP_SEGMENT_SIZE);
            ByteBuf oldBuf = buf;
            if (firstSegmentIndex == lastSegmentIndex) {
                buf = mmapSegment(firstSegmentIndex).retainedSlice(offsetInSegment, Math.toIntExact(length));
            } else {
                CompositeByteBuf composite = Unpooled.compositeBuffer(lastSegmentIndex - firstSegmentIndex + 1);
                composite.addComponent(mmapSegment(firstSegmentIndex).retainedSlice(offsetInSegment, MMAP_SEGMENT_SIZE - offsetInSegment));
                for (int i = firstSegmentIndex + 1; i < lastSegmentIndex; i++) {
                    composite.addComponent(mmapSegment(i).retain());
                }
                composite.addComponent(mmapSegment(lastSegmentIndex).retainedSlice(0, Math.toIntExact((offset + length) % MMAP_SEGMENT_SIZE)));
                buf = composite;
            }
            if (oldBuf != null) {
                oldBuf.release();
            }
        }

        /**
         * Get the contents of this chunk as a {@link ByteBuf}. If there are concurrent operations
         * on this data (e.g. it is being moved to disk), this method may block. Must only be
         * called once.
         *
         * @return The contents of this chunk
         */
        public ByteBuf claim() {
            lock.lock();
            if (buf == null) {
                return Unpooled.EMPTY_BUFFER;
            }
            ByteBuf b = buf;
            buf = null;
            b.touch();
            release();
            return b;
        }

        @Override
        protected void deallocate() {
            if (!lock.tryLock()) {
                // already claimed
                return;
            }
            if (buf != null) {
                buf.release();
                buf = null;
            }
        }

        @Override
        public ReferenceCounted touch() {
            return this;
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }
    }

    /**
     * Factory for {@link MicronautHttpData} instances. Immutable, only some operations are
     * supported.
     */
    @Internal
    public static final class Factory implements HttpDataFactory {
        private final HttpServerConfiguration.MultipartConfiguration multipartConfiguration;
        private final Charset characterEncoding;

        private final Set<MicronautHttpData<?>> toClean = new HashSet<>();

        public Factory(HttpServerConfiguration.MultipartConfiguration multipartConfiguration, Charset characterEncoding) {
            this.multipartConfiguration = multipartConfiguration;
            this.characterEncoding = characterEncoding;
        }

        @Override
        public void setMaxLimit(long max) {
            throw new UnsupportedOperationException();
        }

        public AttributeImpl createAttribute(String name) {
            AttributeImpl attribute = new AttributeImpl(this, name);
            toClean.add(attribute);
            return attribute;
        }

        @Override
        public Attribute createAttribute(HttpRequest request, String name) {
            return createAttribute(name);
        }

        @Override
        public Attribute createAttribute(HttpRequest request, String name, long definedSize) {
            AttributeImpl attribute = createAttribute(name);
            attribute.definedSize = definedSize;
            return attribute;
        }

        @Override
        public Attribute createAttribute(HttpRequest request, String name, String value) {
            AttributeImpl attr = createAttribute(name);
            try {
                attr.addContent(Unpooled.wrappedBuffer(value.getBytes(characterEncoding)), true);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return attr;
        }

        @Override
        public FileUpload createFileUpload(HttpRequest request, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
            FileUploadImpl fileUpload = new FileUploadImpl(this, name, filename, contentType);
            toClean.add(fileUpload);
            fileUpload.definedSize = size;
            fileUpload.charset = charset;
            return fileUpload;
        }

        @Override
        public void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data) {
            //noinspection SuspiciousMethodCalls
            toClean.remove(data);
        }

        @Override
        public void cleanRequestHttpData(HttpRequest request) {
            cleanAllHttpData();
        }

        @Override
        public void cleanAllHttpData() {
            for (MicronautHttpData<?> micronautHttpData : toClean) {
                micronautHttpData.release();
            }
            toClean.clear();
        }

        @Override
        public void cleanRequestHttpDatas(HttpRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cleanAllHttpDatas() {
            throw new UnsupportedOperationException();
        }
    }

    private final class StreamImpl extends InputStream {
        ByteBuf buf = Unpooled.EMPTY_BUFFER;

        @Override
        public int read() throws IOException {
            byte[] arr = new byte[1];
            if (read(arr) != 1) {
                return -1;
            }
            return arr[0] & 0xff;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            if (!buf.isReadable()) {
                buf.release();

                Chunk nextChunk = pollChunk();
                if (nextChunk == null) {
                    buf = Unpooled.EMPTY_BUFFER;
                    return -1;
                }
                buf = nextChunk.claim();
            }
            int n = Math.min(len, buf.readableBytes());
            buf.readBytes(b, off, n);
            return n;
        }

        @Override
        public void close() throws IOException {
            if (buf != null) {
                buf.release();
                buf = null;
                MicronautHttpData.this.release();
            }
        }
    }
}
