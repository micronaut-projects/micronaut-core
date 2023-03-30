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

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.DefaultHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.server.netty.body.MultiObjectBody;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.util.ReferenceCounted;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a {@link MultipartBody} argument.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
public class MultipartBodyArgumentBinder implements NonBlockingBodyArgumentBinder<MultipartBody> {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final BeanLocator beanLocator;
    private final BeanProvider<HttpServerConfiguration> httpServerConfiguration;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator
     * @param httpServerConfiguration The server configuration
     */
    public MultipartBodyArgumentBinder(BeanLocator beanLocator, BeanProvider<HttpServerConfiguration> httpServerConfiguration) {
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public Argument<MultipartBody> argumentType() {
        return Argument.of(MultipartBody.class);
    }

    @Override
    public BindingResult<MultipartBody> bind(ArgumentConversionContext<MultipartBody> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            HttpContentProcessor processor = beanLocator.findBean(HttpContentSubscriberFactory.class,
                    new ConsumesMediaTypeQualifier<>(MediaType.MULTIPART_FORM_DATA_TYPE))
                .map(factory -> factory.build(nhr))
                .orElse(new DefaultHttpContentProcessor(nhr, httpServerConfiguration.get()));
            MultiObjectBody multiObjectBody;
            try {
                multiObjectBody = nhr.rootBody()
                    .processMulti(processor);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            //noinspection RedundantCast,unchecked
            return () -> Optional.of(subscriber -> ((Publisher<HttpData>) multiObjectBody.asPublisher()).subscribe(new CompletionAwareSubscriber<>() {

                Subscription s;
                final AtomicLong partsRequested = new AtomicLong(0);

                final Set<ReferenceCounted> partial = new HashSet<>();

                @Override
                protected void doOnSubscribe(Subscription subscription) {
                    this.s = subscription;
                    subscriber.onSubscribe(new Subscription() {

                        @Override
                        public void request(long n) {
                            if (partsRequested.getAndUpdate(prev -> prev + n) == 0) {
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
                protected void doOnNext(HttpData message) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Server received streaming message for argument [{}]: {}", context.getArgument(), message);
                    }
                    if (message.isCompleted() && message.length() != 0) {
                        partial.remove(message);
                        partsRequested.decrementAndGet();
                        if (message instanceof FileUpload fu) {
                            subscriber.onNext(new NettyCompletedFileUpload(fu, true));
                        } else if (message instanceof Attribute attr) {
                            subscriber.onNext(new NettyCompletedAttribute(attr, true));
                        }
                    } else {
                        partial.add(message);
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
                    releasePartial();
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
                    releasePartial();
                    subscriber.onComplete();
                }

                private void releasePartial() {
                    for (ReferenceCounted rc : partial) {
                        rc.release();
                    }
                    partial.clear();
                }

            }));
        }
        return BindingResult.EMPTY;
    }
}
