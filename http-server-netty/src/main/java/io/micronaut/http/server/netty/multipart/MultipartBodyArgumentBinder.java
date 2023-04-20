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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.multipart.CompletedPart;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
                return () -> Optional.of(Flux.<CompletedPart>error(e)::subscribe);
            }
            Set<ReferenceCounted> partial = new HashSet<>();
            //noinspection unchecked
            Flux<CompletedPart> completed = Flux.from(((Publisher<HttpData>) multiObjectBody.asPublisher())).mapNotNull(message -> {
                if (message.isCompleted() && message.length() != 0) {
                    partial.remove(message);
                    if (message instanceof FileUpload fu) {
                        return new NettyCompletedFileUpload(fu, true);
                    } else {
                        return new NettyCompletedAttribute((Attribute) message, true);
                    }
                } else {
                    partial.add(message);
                    return null;
                }
            }).doOnTerminate(() -> partial.forEach(ReferenceCounted::release));
            return () -> Optional.of(completed::subscribe);
        }
        return BindingResult.empty();
    }
}
