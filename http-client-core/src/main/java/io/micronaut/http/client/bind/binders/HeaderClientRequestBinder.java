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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;

import java.util.List;

public class HeaderClientRequestBinder implements AnnotatedClientRequestBinder<Header> {
    @Override
    public void bind(
            @NonNull MethodInvocationContext<Object, Object> context,
            @NonNull ClientRequestUriContext uriContext,
            @NonNull MutableHttpRequest<?> request
    ) {
        List<AnnotationValue<Header>> headerAnnotations = context.getAnnotationValuesByType(Header.class);
        for (AnnotationValue<Header> headerAnnotation : headerAnnotations) {
            String headerName = headerAnnotation.stringValue("name").orElse(null);
            String headerValue = headerAnnotation.stringValue().orElse(null);
            MutableHttpHeaders headers = request.getHeaders();

            if (StringUtils.isNotEmpty(headerName) &&
                    StringUtils.isNotEmpty(headerValue) &&
                    !headers.contains(headerName)
            ) {
                headers.set(headerName, headerValue);
            }
        }
    }

    @Override
    @NonNull
    public Class<Header> getAnnotationType() {
        return Header.class;
    }
}