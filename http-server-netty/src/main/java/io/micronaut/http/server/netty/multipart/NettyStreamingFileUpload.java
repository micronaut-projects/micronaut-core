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
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/**
 * An implementation of the {@link StreamingFileUpload} interface for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyStreamingFileUpload implements StreamingFileUpload {

    private static final Logger LOG = LoggerFactory.getLogger(NettyStreamingFileUpload.class);
    private io.netty.handler.codec.http.multipart.FileUpload fileUpload;
    private final ExecutorService ioExecutor;
    private final HttpServerConfiguration.MultipartConfiguration configuration;
    private final Flowable subject;

    /**
     * @param httpData               The file upload (the data)
     * @param multipartConfiguration The multipart configuration
     * @param ioExecutor             The IO executor
     * @param subject                The subject
     */
    public NettyStreamingFileUpload(
        io.netty.handler.codec.http.multipart.FileUpload httpData,
        HttpServerConfiguration.MultipartConfiguration multipartConfiguration,
        ExecutorService ioExecutor,
        Flowable subject) {

        this.configuration = multipartConfiguration;
        this.fileUpload = httpData;
        this.ioExecutor = ioExecutor;
        this.subject = subject;
    }

    @Override
    public Optional<MediaType> getContentType() {
        try {
            return Optional.of(new MediaType(fileUpload.getContentType()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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
        BooleanSupplier transferOperation = () -> {
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Transferring file {} to location {}", fileUpload.getFilename(), destination);
                }
                return destination != null && fileUpload.renameTo(destination);
            } catch (IOException e) {
                throw new MultipartException("Error transferring file: " + fileUpload.getName(), e);
            }
        };

        return Single.<Boolean>create(emitter ->

            subject.subscribeOn(Schedulers.from(ioExecutor))
                .subscribe(new Subscriber() {
                    Subscription subscription;
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscription = s;
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(Object o) {
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        emitter.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        if (fileUpload.isCompleted()) {
                            try {
                                emitter.onSuccess(transferOperation.getAsBoolean());
                            } catch (Exception e) {
                                emitter.onError(e);
                            }
                        } else {
                            emitter.onError(new MultipartException("Transfer did not complete"));
                        }
                    }
                })
        ).toFlowable();

    }

    @Override
    public Publisher<Boolean> delete() {
        return new AsyncSingleResultPublisher<>(ioExecutor, () -> {
            fileUpload.delete();
            return true;
        });
    }

    /**
     * @param location The location for the temp file
     * @return The temporal file
     */
    protected File createTemp(String location) {
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
    public void subscribe(Subscriber<? super PartData> s) {
        subject.subscribe(s);
    }
}
