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

import io.micronaut.context.BeanLocator;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.*;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.EmptyByteBuf;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a {@link MultipartBody} argument.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public class MultipartBodyArgumentBinder implements NonBlockingBodyArgumentBinder<MultipartBody> {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    
    private final BeanLocator beanLocator;
    private final HttpServerConfiguration httpServerConfiguration;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator
     * @param httpServerConfiguration The server configuration
     */
    public MultipartBodyArgumentBinder(BeanLocator beanLocator, HttpServerConfiguration httpServerConfiguration) {
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public boolean supportsSuperTypes() {
        return false;
    }

    @Override
    public Argument<MultipartBody> argumentType() {
        return Argument.of(MultipartBody.class);
    }

    @Override
    public BindingResult<MultipartBody> bind(ArgumentConversionContext<MultipartBody> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {
                HttpContentProcessor<?> processor = beanLocator.findBean(HttpContentSubscriberFactory.class,
                        new ConsumesMediaTypeQualifier<>(MediaType.MULTIPART_FORM_DATA_TYPE))
                        .map(factory -> factory.build(nettyHttpRequest))
                        .orElse(new DefaultHttpContentProcessor(nettyHttpRequest, httpServerConfiguration));

                //noinspection unchecked
                return () -> Optional.of(subscriber -> processor.subscribe(new TypedSubscriber<Object>((Argument) context.getArgument()) {

                    Subscription s;
                    AtomicLong partsRequested = new AtomicLong(0);

                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        this.s = subscription;
                        subscriber.onSubscribe(new Subscription() {

                            @Override
                            public void request(long n) {
                                if (partsRequested.getAndUpdate((prev) -> prev + n) == 0) {
                                    s.request(n);
                                }
                            }

                            @Override
                            public void cancel() {
                                subscription.cancel();
                            }
                        });
                    }

                    @Override
                    protected void doOnNext(Object message) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Server received streaming message for argument [{}]: {}", context.getArgument(), message);
                        }
                        if (message instanceof ByteBufHolder) {
                            if (((ByteBufHolder) message).content() instanceof EmptyByteBuf) {
                                return;
                            }
                        }

                        if (message instanceof HttpData) {
                            HttpData data = (HttpData) message;
                            if (data.isCompleted()) {
                                partsRequested.decrementAndGet();
                                if (data instanceof FileUpload) {
                                    subscriber.onNext(new NettyCompletedFileUpload((FileUpload) data, false));
                                } else if (data instanceof Attribute) {
                                    subscriber.onNext(new NettyCompletedAttribute((Attribute) data, false));
                                }

                                //If the user didn't release the data, we should
                                if (data.refCnt() > 0) {
                                    data.release();
                                }
                            }
                        }

                        if (partsRequested.get() > 0) {
                            s.request(1);
                        }
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Server received error for argument [" + context.getArgument() + "]: " + t.getMessage(), t);
                        }
                        try {
                            subscriber.onError(t);
                        } finally {
                            s.cancel();
                        }
                    }

                    @Override
                    protected void doOnComplete() {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Done receiving messages for argument: {}", context.getArgument());
                        }
                        subscriber.onComplete();
                    }

                }));
            }
        }
        return BindingResult.EMPTY;
    }
}
