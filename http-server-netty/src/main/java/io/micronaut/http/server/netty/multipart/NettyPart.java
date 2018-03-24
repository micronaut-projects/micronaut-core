/*
 * Copyright 2017 original authors
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
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.micronaut.core.async.processor.SingleSubscriberProcessor;
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * An implementation of the {@link StreamingFileUpload} interface for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyPart extends SingleSubscriberProcessor<io.netty.handler.codec.http.multipart.FileUpload, StreamingFileUpload> implements StreamingFileUpload, ByteBufHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NettyPart.class);
    private io.netty.handler.codec.http.multipart.FileUpload fileUpload;
    private final ExecutorService ioExecutor;
    private final HttpServerConfiguration.MultipartConfiguration configuration;
    private final Subscription dataSubscription;

    public NettyPart(
            io.netty.handler.codec.http.multipart.FileUpload httpData,
            HttpServerConfiguration.MultipartConfiguration multipartConfiguration,
            ExecutorService ioExecutor,
            Subscription subscription) {
        this.configuration = multipartConfiguration;
        this.fileUpload = httpData;
        this.ioExecutor = ioExecutor;
        this.dataSubscription = subscription;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteBufInputStream(fileUpload.getByteBuf());
    }

    @Override
    public byte[] getBytes() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return ByteBufUtil.getBytes(byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public ByteBuffer getByteBuffer() throws IOException {
        ByteBuf byteBuf = getByteBuf();
        try {
            return byteBuf.nioBuffer();
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(new MediaType(fileUpload.getContentType()));
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

    @Override
    public Publisher<Boolean> transferTo(String location) {
        String baseDirectory = configuration.getLocation().map(File::getAbsolutePath).orElse(DiskFileUpload.baseDirectory);
        File file = baseDirectory == null ? createTemp(location) : new File(baseDirectory, location);
        return transferTo(file);
    }

    @Override
    public Publisher<Boolean> transferTo(File destination) {
        Supplier<Boolean> transferOperation = () -> {
            try {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Transferring file {} to location {}", fileUpload.getFilename(), destination);
                }
                return destination != null && fileUpload.renameTo(destination);
            } catch (IOException e) {
                throw new MultipartException("Error transferring file: " + fileUpload.getName(), e);
            }
            finally {
                if(fileUpload.refCnt() > 0) {
                    fileUpload.release();
                }
            }
        };
        if (isComplete()) {
            return new AsyncSingleResultPublisher<>(ioExecutor, transferOperation);
        } else {
            fileUpload.retain();
            return new SingleSubscriberProcessor<StreamingFileUpload, Boolean>() {

                @Override
                protected void doOnNext(StreamingFileUpload message) {
                    if (message.isComplete()) {
                        ioExecutor.submit(() -> {
                                    Subscriber<? super Boolean> subscriber = getSubscriber();
                                    try {
                                        subscriber.onNext(transferOperation.get());
                                        subscriber.onComplete();
                                    } catch (Exception e) {
                                        subscriber.onError(e);
                                    }
                                }
                        );
                    }
                    else {
                        getSubscriber().onError(new MultipartException("Transfer did not complete"));
                    }
                }

                @Override
                protected void doOnComplete() {
                    super.doOnComplete();
                }

                @Override
                protected void doSubscribe(Subscriber<? super Boolean> subscriber) {
                    NettyPart.this.subscribe(this);
                }
            };
        }
    }

    @Override
    public Publisher<Boolean> delete() {
        return new AsyncSingleResultPublisher<>(ioExecutor, () -> {
            fileUpload.delete();
            return true;
        });
    }

    protected ByteBuf getByteBuf() throws IOException {
        ByteBuf byteBuf;
        if (fileUpload instanceof ChunkedFileUpload) {
            ChunkedFileUpload chunkedFileUpload = (ChunkedFileUpload) fileUpload;
            byteBuf = chunkedFileUpload.getCurrentChunk();
        } else {
            byteBuf = fileUpload.getByteBuf();
        }
        return byteBuf;
    }

    @Override
    public ByteBuf content() {
        return fileUpload.content();
    }

    protected File createTemp(String location)  {
        File tempFile;
        try {
            tempFile = File.createTempFile(DiskFileUpload.prefix, DiskFileUpload.postfix + '_' + location);
        } catch (IOException e) {
            throw new MultipartException("Unable to create temp directory: " + e.getMessage(), e);
        }
        if (tempFile.delete()) {
            return tempFile;
        }
        return null;
    }

    @Override
    public ByteBufHolder copy() {
        return fileUpload.copy();
    }

    @Override
    public ByteBufHolder duplicate() {
        return fileUpload.duplicate();
    }

    @Override
    public ByteBufHolder retainedDuplicate() {
        return fileUpload.retainedDuplicate();
    }

    @Override
    public ByteBufHolder replace(ByteBuf content) {
        return fileUpload.replace(content);
    }

    @Override
    public int refCnt() {
        return fileUpload.refCnt();
    }

    @Override
    public ByteBufHolder retain() {
        return fileUpload.retain();
    }

    @Override
    public ByteBufHolder retain(int increment) {
        return fileUpload.retain(increment);
    }

    @Override
    public ByteBufHolder touch() {
        return fileUpload.touch();
    }

    @Override
    public ByteBufHolder touch(Object hint) {
        return fileUpload.touch(hint);
    }

    @Override
    public boolean release() {
        return fileUpload.release();
    }

    @Override
    public boolean release(int decrement) {
        return fileUpload.release(decrement);
    }


    @Override
    protected void doOnNext(io.netty.handler.codec.http.multipart.FileUpload message) {
        this.fileUpload = message;
        Optional<Subscriber<? super StreamingFileUpload>> currentSubscriber = currentSubscriber();
        if (currentSubscriber.isPresent()) {
            Subscriber<? super StreamingFileUpload> subscriber = currentSubscriber.get();
            if (message.isCompleted()) {
                subscriber.onNext(this);
            } else {
                dataSubscription.request(1);
            }
        }

    }

    @Override
    protected void doOnComplete() {
        if (!isComplete()) {
            super.doOnComplete();
        }
    }

    @Override
    protected void doSubscribe(Subscriber<? super StreamingFileUpload> subscriber) {
        subscriber.onSubscribe(dataSubscription);
    }
}
