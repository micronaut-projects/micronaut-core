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
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.DefaultHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorAsReactiveProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.server.netty.binders.StreamedNettyRequestArgumentBinder;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a {@link MultipartBody} argument.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
public class MultipartBodyArgumentBinder implements NonBlockingBodyArgumentBinder<MultipartBody>, StreamedNettyRequestArgumentBinder<MultipartBody> {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final BeanLocator beanLocator;
    private final BeanProvider<HttpServerConfiguration> httpServerConfiguration;

    /**
     * Default constructor.
     *
     * @param beanLocator             The bean locator
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
    public BindingResult<MultipartBody> bindForStreamedNettyRequest(ArgumentConversionContext<MultipartBody> context, StreamedHttpRequest streamedHttpRequest, NettyHttpRequest<?> nettyHttpRequest) {
        HttpContentProcessor processor = beanLocator.findBean(HttpContentSubscriberFactory.class,
                new ConsumesMediaTypeQualifier<>(MediaType.MULTIPART_FORM_DATA_TYPE))
            .map(factory -> factory.build(nettyHttpRequest))
            .orElseGet(() -> new DefaultHttpContentProcessor(nettyHttpRequest, httpServerConfiguration.get()));

        nettyHttpRequest.setUsesHttpContentProcessor();

        Flux<CompletedPart> flux = Flux.from(HttpContentProcessorAsReactiveProcessor.<HttpData>asPublisher(processor.resultType(context.getArgument()), nettyHttpRequest))
            .flatMap(message -> {
                // MicronautHttpData does not support .content()
                if (message.length() == 0) {
                    return Flux.empty();
                }
                if (message.isCompleted()) {
                    if (message instanceof FileUpload fu) {
                        return Flux.just(new NettyCompletedFileUpload(fu, false))
                            .doOnComplete(message::release);
                    } else if (message instanceof Attribute attr) {
                        return Flux.just(new NettyCompletedAttribute(attr, false))
                            .doOnComplete(message::release);
                    }
                }
                message.release();
                return Flux.empty();
            });

        return () -> Optional.of((MultipartBody) flux::subscribe);
    }

}
