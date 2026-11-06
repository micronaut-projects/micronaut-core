/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route with a subclass of {@link UriMatchTemplate} does not read the ambiguity statistics of
 * its template when it is built: the router asks the template for them when it needs them.
 */
class UriMatchTemplateSubclassStatisticsTest {

    @Test
    void aSubclassIsNotAskedForItsStatisticsWhenTheRouteIsBuilt() {
        CountingTemplate template = new CountingTemplate("/items/{id}");
        new DefaultUrlRouteInfo<>(HttpMethod.GET, template, StandardCharsets.UTF_8,
            HandlerMethod.of((RequestHandler) (request, pathVariables) -> HttpResponse.ok()),
            null, null, List.of(), List.of(), List.of(), null, ConversionService.SHARED,
            new NoExecutorSelector(), MessageBodyHandlerRegistry.EMPTY);
        assertEquals(0, template.reads.get());
    }

    static final class CountingTemplate extends UriMatchTemplate {
        final AtomicInteger reads = new AtomicInteger();

        CountingTemplate(CharSequence template) {
            super(template);
        }

        @Override
        public long getPathVariableSegmentCount() {
            reads.incrementAndGet();
            return super.getPathVariableSegmentCount();
        }

        @Override
        public int getRawSegmentLength() {
            reads.incrementAndGet();
            return super.getRawSegmentLength();
        }
    }

    static final class NoExecutorSelector implements ExecutorSelector {
        @Override
        public Optional<java.util.concurrent.ExecutorService> select(io.micronaut.inject.MethodReference<?, ?> method,
                                                                     io.micronaut.scheduling.executor.ThreadSelection threadSelection) {
            return Optional.empty();
        }

        @Override
        public Optional<java.util.concurrent.ExecutorService> select(String name) {
            return Optional.empty();
        }
    }
}
