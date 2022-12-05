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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * <p>Decodes {@link MediaType#MULTIPART_FORM_DATA} in a non-blocking manner.</p>
 * <p>
 * <p>Designed to be used by a single thread</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class FormDataHttpContentProcessor extends AbstractHttpContentProcessor {

    private final InterfaceHttpPostRequestDecoder decoder;
    private final boolean enabled;
    private final long partMaxSize;

    /**
     * Set to true to request a destroy by any thread.
     */
    private volatile boolean pleaseDestroy = false;
    /**
     * {@code true} during {@link #doOnNext}, can't destroy while that's running.
     */
    private volatile boolean inFlight = false;
    /**
     * {@code true} if the decoder has been destroyed or will be destroyed in the near future.
     */
    private boolean destroyed = false;

    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link NettyHttpServerConfiguration}
     */
    FormDataHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, NettyHttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
        Charset characterEncoding = nettyHttpRequest.getCharacterEncoding();
        HttpServerConfiguration.MultipartConfiguration multipart = configuration.getMultipart();
        HttpDataFactory factory = new MicronautHttpData.Factory(multipart, characterEncoding);
        final HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
        if (HttpPostRequestDecoder.isMultipart(nativeRequest)) {
            this.decoder = new MicronautHttpPostMultipartRequestDecoder(factory, nativeRequest, characterEncoding);
        } else {
            this.decoder = new HttpPostStandardRequestDecoder(factory, nativeRequest, characterEncoding);
        }
        this.enabled = nettyHttpRequest.getContentType().map(type -> type.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)).orElse(false) ||
            multipart.isEnabled();
        this.partMaxSize = multipart.getMaxFileSize();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    protected void onData(ByteBufHolder message) {
        boolean skip;
        synchronized (this) {
            if (destroyed) {
                skip = true;
            } else {
                skip = false;
                inFlight = true;
            }
        }
        if (skip) {
            message.release();
            return;
        }
        try {
            if (message instanceof HttpContent) {
                HttpContent httpContent = (HttpContent) message;
                boolean anyResults = false;

                try {
                    InterfaceHttpPostRequestDecoder postRequestDecoder = this.decoder;
                    postRequestDecoder.offer(httpContent);

                    while (postRequestDecoder.hasNext()) {
                        InterfaceHttpData data = postRequestDecoder.next();
                        data.touch();
                        switch (data.getHttpDataType()) {
                            case Attribute:
                                Attribute attribute = (Attribute) data;
                                // bodyListHttpData keeps a copy and releases it later
                                offer(attribute.retain());
                                anyResults = true;
                                postRequestDecoder.removeHttpDataFromClean(attribute);
                                break;
                            case FileUpload:
                                FileUpload fileUpload = (FileUpload) data;
                                if (fileUpload.isCompleted()) {
                                    // bodyListHttpData keeps a copy and releases it later
                                    offer(fileUpload.retain());
                                    anyResults = true;
                                    postRequestDecoder.removeHttpDataFromClean(fileUpload);
                                }
                                break;
                            default:
                                // no-op
                        }
                    }

                    InterfaceHttpData currentPartialHttpData = postRequestDecoder.currentPartialHttpData();
                    if (currentPartialHttpData instanceof HttpData) {
                        // can't give away ownership of this data yet, so retain it
                        offer(currentPartialHttpData.retain());
                        anyResults = true;
                    }

                } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
                    // ok, ignore
                } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException && cause.getMessage().equals("Size exceed allowed maximum capacity")) {
                        String partName = decoder.currentPartialHttpData().getName();
                        throw new ContentLengthExceededException("The part named [" + partName + "] exceeds the maximum allowed content length [" + partMaxSize + "]");
                    } else {
                        throw e;
                    }
                } finally {
                    if (!anyResults) {
                        requestInput();
                    }

                    httpContent.release();
                }
            } else {
                message.release();
            }
        } finally {
            inFlight = false;
            destroyIfRequested();
        }
    }

    @Override
    public void add(ByteBufHolder message) throws Throwable {
        try {
            super.add(message);
        } catch (Throwable e) {
            complete();
            throw e;
        }
    }

    @Override
    public void complete() {
        pleaseDestroy = true;
        destroyIfRequested();
    }

    @Override
    public void cancel() {
        complete();
    }

    private void destroyIfRequested() {
        boolean destroy;
        synchronized (this) {
            if (pleaseDestroy && !destroyed && !inFlight) {
                destroy = true;
                destroyed = true;
            } else {
                destroy = false;
            }
        }
        if (destroy) {
            decoder.destroy();
        }
    }

}
