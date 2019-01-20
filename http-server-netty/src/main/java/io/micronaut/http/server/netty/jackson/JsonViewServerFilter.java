/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.jackson.JacksonConfiguration;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Jackson @JsonView filter.
 */
@Requires(beans = JacksonConfiguration.class)
@Requires(property = "jackson.json-view-enabled")
@Filter("/**")
public class JsonViewServerFilter implements HttpServerFilter {
    private static final Logger LOG = LoggerFactory.getLogger(JsonViewServerFilter.class);

    private JsonViewMediaTypeCodecFactory codecFactory;

    /**
     * @param codecFactory The factory to produce @JsonView enabled codecs
     */
    public JsonViewServerFilter(JsonViewMediaTypeCodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<AnnotationMetadata> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, AnnotationMetadata.class);
        if (routeMatch.isPresent()) {
            AnnotationMetadata metadata = routeMatch.get();

            Optional<Class> viewClass = metadata.classValue(JsonView.class);
            if (viewClass.isPresent()) {
                MediaTypeCodec codec = codecFactory.createJsonViewCodec(viewClass.get());
                request.setAttribute(HttpAttributes.MEDIA_TYPE_CODEC, codec);
            }
        }

        return chain.proceed(request);
    }
}
