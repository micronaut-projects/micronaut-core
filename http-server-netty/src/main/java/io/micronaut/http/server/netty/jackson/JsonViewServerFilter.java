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
package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteInfo;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Jackson @JsonView filter.
 *
 * @since 1.1
 * @author mmindenhall
 * @author graemerocher
 */
@Requires(beans = JsonConfiguration.class)
@Requires(classes = JsonView.class)
@Requires(property = JsonViewServerFilter.PROPERTY_JSON_VIEW_ENABLED)
@ServerFilter("/**")
@Internal
public class JsonViewServerFilter implements Ordered {

    /**
     * Property used to specify whether JSON view is enabled.
     */
    public static final String PROPERTY_JSON_VIEW_ENABLED = "jackson.json-view.enabled";

    private final JsonViewCodecResolver codecFactory;
    private final ExecutorService executorService;
    private final ConversionService conversionService;

    /**
     * @param jsonViewCodecResolver The JSON view codec resolver.
     * @param executorService       The I/O executor service
     * @param conversionService     The conversion service
     */
    public JsonViewServerFilter(JsonViewCodecResolver jsonViewCodecResolver,
                                @Named(TaskExecutors.BLOCKING) ExecutorService executorService,
                                ConversionService conversionService) {
        this.codecFactory = jsonViewCodecResolver;
        this.executorService = executorService;
        this.conversionService = conversionService;
    }

    @ResponseFilter
    public final Publisher<? extends MutableHttpResponse<?>> doFilter(HttpRequest<?> request, MutableHttpResponse<?> response) {
        final RouteInfo<?> routeInfo = request.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);
        if (routeInfo != null) {
            final Optional<Class<?>> viewClass = routeInfo.findAnnotation(JsonView.class)
                .flatMap(AnnotationValue::classValue);
            if (viewClass.isPresent() &&
                // if this is an error response, the response body does not come from the controller
                !response.getAttributes().contains(HttpAttributes.EXCEPTION)) {

                final Optional<?> optionalBody = response.getBody();
                if (optionalBody.isPresent()) {
                    Object body = optionalBody.get();
                    MediaTypeCodec codec = codecFactory.resolveJsonViewCodec(viewClass.get());
                    if (Publishers.isConvertibleToPublisher(body)) {
                        Publisher<?> pub = Publishers.convertPublisher(conversionService, body, Publisher.class);
                        response.body(Flux.from(pub)
                            .map(o -> codec.encode((Argument) routeInfo.getResponseBodyType(), o))
                            .subscribeOn(Schedulers.fromExecutorService(executorService)));
                    } else {
                        return Mono.fromCallable(() -> {
                            @SuppressWarnings({"unchecked", "rawtypes"}) final byte[] encoded = codec.encode((Argument) routeInfo.getResponseBodyType(), body);
                            response.body(encoded);
                            return response;
                        }).subscribeOn(Schedulers.fromExecutorService(executorService));
                    }
                }
            }
        }
        return Mono.just(response);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.RENDERING.order();
    }
}
