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
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.functional.ThrowingSupplier;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.stream.PublisherAsBlocking;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.netty.PublisherAsStream;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * An implementation of the {@link StreamingFileUpload} interface for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class NettyStreamingFileUpload implements StreamingFileUpload {

    private static final Logger LOG = LoggerFactory.getLogger(NettyStreamingFileUpload.class);
    private io.netty.handler.codec.http.multipart.FileUpload fileUpload;
    private final ExecutorService ioExecutor;
    private final HttpServerConfiguration.MultipartConfiguration configuration;
    private final Flux<PartData> subject;

    private NettyStreamingFileUpload(
        io.netty.handler.codec.http.multipart.FileUpload httpData,
        HttpServerConfiguration.MultipartConfiguration multipartConfiguration,
        ExecutorService ioExecutor,
        Flux<PartData> subject) {

        this.configuration = multipartConfiguration;
        this.fileUpload = httpData;
        this.ioExecutor = ioExecutor;
        this.subject = subject;
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.of(new MediaType(fileUpload.getContentType(), NameUtils.extension(fileUpload.getFilename())));
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

    @Override
    public Publisher<Boolean> transferTo(String location) {
        String baseDirectory = configuration.getLocation().map(File::getAbsolutePath).orElse(DiskFileUpload.baseDirectory);
        File file = baseDirectory == null ? createTemp(location) : new File(baseDirectory, location);
        return transferTo(file);
    }

    @Override
    public Publisher<Boolean> transferTo(File destination) {
        return transferTo(() -> Files.newOutputStream(destination.toPath()));
    }

    @Override
    public Publisher<Boolean> transferTo(OutputStream outputStream) {
        return transferTo(() -> outputStream);
    }

    @Override
    public Publisher<Boolean> delete() {
        return new AsyncSingleResultPublisher<>(ioExecutor, () -> {
            fileUpload.delete();
            return true;
        });
    }

    @Override
    public InputStream asInputStream() {
        PublisherAsBlocking<ByteBuf> publisherAsBlocking = new PublisherAsBlocking<>() {
            @Override
            protected void release(ByteBuf item) {
                item.release();
            }
        };
        subject.map(pd -> ((NettyPartData) pd).getByteBuf()).subscribe(publisherAsBlocking);
        return new PublisherAsStream(publisherAsBlocking);
    }

    /**
     * @param location The location for the temp file
     * @return The temporal file
     */
    protected File createTemp(String location) {
        try {
            return Files.createTempFile(DiskFileUpload.prefix, DiskFileUpload.postfix + '_' + location).toFile();
        } catch (IOException e) {
            throw new MultipartException("Unable to create temp file: " + e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(Subscriber<? super PartData> s) {
        subject.subscribe(s);
    }

    @Override
    public void discard() {
        fileUpload.release();
    }

    private Publisher<Boolean> transferTo(ThrowingSupplier<OutputStream, IOException> outputStreamSupplier) {
        return Mono.<Boolean>create(emitter ->

                subject.publishOn(Schedulers.fromExecutorService(ioExecutor))
                        .subscribe(new Subscriber<PartData>() {
                            Subscription subscription;
                            OutputStream outputStream;
                            @Override
                            public void onSubscribe(Subscription s) {
                                subscription = s;
                                subscription.request(1);
                                try {
                                    outputStream = outputStreamSupplier.get();
                                } catch (IOException e) {
                                    handleError(e);
                                }
                            }

                            @Override
                            public void onNext(PartData o) {
                                try {
                                    outputStream.write(o.getBytes());
                                    subscription.request(1);
                                } catch (IOException e) {
                                    handleError(e);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                discard();
                                emitter.error(t);
                                try {
                                    if (outputStream != null) {
                                        outputStream.close();
                                    }
                                } catch (IOException e) {
                                    if (LOG.isWarnEnabled()) {
                                        LOG.warn("Failed to close file stream : {}", fileUpload.getName());
                                    }
                                }
                            }

                            @Override
                            public void onComplete() {
                                discard();
                                try {
                                    outputStream.close();
                                    emitter.success(true);
                                } catch (IOException e) {
                                    if (LOG.isWarnEnabled()) {
                                        LOG.warn("Failed to close file stream : {}", fileUpload.getName());
                                    }
                                    emitter.success(false);
                                }
                            }

                            private void handleError(Throwable t) {
                                subscription.cancel();
                                onError(new MultipartException("Error transferring file: " + fileUpload.getName(), t));
                            }
                        })
        ).flux();
    }

    /**
     * Factory for instances of {@link NettyStreamingFileUpload}. Wraps the fixed requirements that
     * don't depend on request.
     *
     * @param ioExecutor The IO executor
     * @param multipartConfiguration The multipart configuration
     */
    @Internal
    public record Factory(
        HttpServerConfiguration.MultipartConfiguration multipartConfiguration,
        ExecutorService ioExecutor
    ) {
        public NettyStreamingFileUpload create(io.netty.handler.codec.http.multipart.FileUpload httpData,
                                               Flux<PartData> subject) {
            return new NettyStreamingFileUpload(httpData, multipartConfiguration, ioExecutor, subject);
        }
    }
}
