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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.EmptyByteBuf;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Optional;

/**
 * Responsible for binding to a {@link InputStream} argument from the body of the request.
 *
 * @author James Kleeh
 * @since 2.5.0
 */
@Internal
public class InputStreamBodyBinder implements NonBlockingBodyArgumentBinder<InputStream> {

    public static final Argument<InputStream> TYPE = Argument.of(InputStream.class);
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final HttpContentProcessorResolver processorResolver;

    /**
     * @param processorResolver The http content processor resolver
     */
    public InputStreamBodyBinder(HttpContentProcessorResolver processorResolver) {
        this.processorResolver = processorResolver;
    }

    @Override
    public Argument<InputStream> argumentType() {
        return TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BindingResult<InputStream> bind(ArgumentConversionContext<InputStream> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {
                PipedOutputStream outputStream = new PipedOutputStream();
                try {
                    PipedInputStream inputStream = new PipedInputStream(outputStream) {
                        private volatile HttpContentProcessor<ByteBufHolder> processor;

                        private void init() {
                            if (processor == null) {
                                processor = (HttpContentProcessor<ByteBufHolder>) processorResolver.resolve(nettyHttpRequest, context.getArgument());
                                processor.subscribe(new CompletionAwareSubscriber<ByteBufHolder>() {

                                    @Override
                                    protected void doOnSubscribe(Subscription subscription) {
                                        subscription.request(1);
                                    }

                                    @Override
                                    protected synchronized void doOnNext(ByteBufHolder message) {
                                        if (LOG.isTraceEnabled()) {
                                            LOG.trace("Server received streaming message for argument [{}]: {}", context.getArgument(), message);
                                        }
                                        ByteBuf content = message.content();
                                        if (!(content instanceof EmptyByteBuf)) {
                                            try {
                                                byte[] bytes = ByteBufUtil.getBytes(content);
                                                outputStream.write(bytes, 0, bytes.length);
                                            } catch (IOException e) {
                                                subscription.cancel();
                                                return;
                                            } finally {
                                                content.release();
                                            }
                                        }
                                        subscription.request(1);
                                    }

                                    @Override
                                    protected synchronized void doOnError(Throwable t) {
                                        if (LOG.isTraceEnabled()) {
                                            LOG.trace("Server received error for argument [" + context.getArgument() + "]: " + t.getMessage(), t);
                                        }
                                        try {
                                            outputStream.close();
                                        } catch (IOException ignored) {
                                        } finally {
                                            subscription.cancel();
                                        }
                                    }

                                    @Override
                                    protected synchronized void doOnComplete() {
                                        if (LOG.isTraceEnabled()) {
                                            LOG.trace("Done receiving messages for argument: {}", context.getArgument());
                                        }
                                        try {
                                            outputStream.close();
                                        } catch (IOException ignored) {
                                        }
                                    }
                                });
                            }
                        }

                        @Override
                        public synchronized int read(byte[] b, int off, int len) throws IOException {
                            init();
                            return super.read(b, off, len);
                        }

                        @Override
                        public synchronized int read() throws IOException {
                            init();
                            return super.read();
                        }
                    };

                    return () -> Optional.of(inputStream);
                } catch (IOException e) {
                    context.reject(e);
                }
            }
        }
        return BindingResult.EMPTY;
    }
}
