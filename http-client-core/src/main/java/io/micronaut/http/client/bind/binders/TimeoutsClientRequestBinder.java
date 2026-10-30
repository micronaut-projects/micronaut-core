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
package io.micronaut.http.client.bind.binders;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.RequestTimeouts;
import io.micronaut.http.client.annotation.ClientTimeouts;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * An {@link AnnotatedClientRequestBinder} implementation for {@link ClientTimeouts}.
 *
 * @author Adam Kobor
 * @since 5.3.0
 */
@Experimental
public class TimeoutsClientRequestBinder implements AnnotatedClientRequestBinder<ClientTimeouts> {
    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service that reads the durations
     */
    public TimeoutsClientRequestBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public void bind(MethodInvocationContext<Object, Object> context,
                     ClientRequestUriContext uriContext,
                     MutableHttpRequest<?> request) {
        AnnotationValue<ClientTimeouts> annotation = context.getAnnotation(ClientTimeouts.class);
        if (annotation == null) {
            return;
        }
        RequestTimeouts timeouts = RequestTimeouts.builder()
            .readTimeout(duration(annotation, "read"))
            .requestTimeout(duration(annotation, "request"))
            .build();
        if (!timeouts.equals(RequestTimeouts.getDefault())) {
            ClientAttributes.setTimeouts(request, timeouts);
        }
    }

    private @Nullable Duration duration(AnnotationValue<ClientTimeouts> annotation, String member) {
        String value = annotation.stringValue(member).orElse(null);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return conversionService.convert(value, Duration.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid duration for @ClientTimeouts(" + member + "): " + value));
    }

    @Override
    public Class<ClientTimeouts> getAnnotationType() {
        return ClientTimeouts.class;
    }
}
