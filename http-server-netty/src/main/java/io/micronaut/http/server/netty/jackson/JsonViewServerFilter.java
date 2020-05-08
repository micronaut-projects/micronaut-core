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
package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Jackson @JsonView filter.
 *
 * @since 1.1
 * @author mmindenhall
 * @author graemerocher
 */
@Requires(beans = JacksonConfiguration.class)
@Requires(property = JsonViewServerFilter.PROPERTY_JSON_VIEW_ENABLED)
@Filter("/**")
public class JsonViewServerFilter implements HttpServerFilter {

    /**
     * Property used to specify whether JSON view is enabled.
     */
    public static final String PROPERTY_JSON_VIEW_ENABLED = "jackson.json-view.enabled";

    private final JsonViewCodecResolver codecFactory;
    private final ExecutorService executorService;

    /**
     * @param jsonViewCodecResolver The JSON view codec resolver.
     * @param executorService The I/O executor service
     */
    public JsonViewServerFilter(
            JsonViewCodecResolver jsonViewCodecResolver,
            @Named(TaskExecutors.IO) ExecutorService executorService) {
        this.codecFactory = jsonViewCodecResolver;
        this.executorService = executorService;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<Class> viewClass = request.getAttribute(HttpAttributes.ROUTE_MATCH, AnnotationMetadata.class)                                                          .flatMap(ann -> ann.classValue(JsonView.class));
        final Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(request);
        if (viewClass.isPresent()) {
            return Flowable.fromPublisher(responsePublisher).switchMap(response -> {
                final Optional<?> optionalBody = response.getBody();
                if (optionalBody.isPresent()) {
                    Object body = optionalBody.get();
                    MediaTypeCodec codec = codecFactory.resolveJsonViewCodec(viewClass.get());
                    if (Publishers.isConvertibleToPublisher(body)) {
                        response.body(Publishers.convertPublisher(body, Flowable.class)
                                .map(item -> codec.encode(item))
                                .subscribeOn(Schedulers.from(executorService)));
                    } else {
                        return Flowable.fromCallable(() -> {
                            final byte[] encoded = codec.encode(body);
                            response.body(encoded);
                            return response;
                        }).subscribeOn(Schedulers.from(executorService));
                    }
                }
                return Flowable.just(response);
            });
        } else {
            return responsePublisher;
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.RENDERING.order();
    }
}
