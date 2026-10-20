/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AnnotatedClientRequestBinder} implementation for {@link Header}.
 */
public class HeaderClientRequestBinder implements AnnotatedClientRequestBinder<Header> {
    @Override
    public void bind(MethodInvocationContext<Object, Object> context,
                     ClientRequestUriContext uriContext,
                     MutableHttpRequest<?> request) {
        // The headers of the method are kept by the method
        List<String> headers = context.getCachedValue(Headers.class, Headers::of).namesAndValues();
        if (!headers.isEmpty()) {
            MutableHttpHeaders requestHeaders = request.getHeaders();
            for (int i = 0; i < headers.size(); i += 2) {
                requestHeaders.setIfMissing(headers.get(i), headers.get(i + 1));
            }
        }
    }

    @Override
    public Class<Header> getAnnotationType() {
        return Header.class;
    }

    /**
     * The headers of a client method, as name/value pairs, skipping a header without a name or a value.
     *
     * @param namesAndValues The names and values
     */
    private record Headers(List<String> namesAndValues) {

        static Headers of(ExecutableMethod<?, ?> method) {
            List<AnnotationValue<Header>> headerAnnotations = method.getAnnotationValuesByType(Header.class);
            List<String> namesAndValues = new ArrayList<>(headerAnnotations.size() * 2);
            for (AnnotationValue<Header> headerAnnotation : headerAnnotations) {
                String headerName = headerAnnotation.stringValue("name").orElse(null);
                String headerValue = headerAnnotation.stringValue().orElse(null);
                if (StringUtils.isNotEmpty(headerName) && StringUtils.isNotEmpty(headerValue)) {
                    namesAndValues.add(headerName);
                    namesAndValues.add(headerValue);
                }
            }
            return new Headers(List.copyOf(namesAndValues));
        }
    }

}
