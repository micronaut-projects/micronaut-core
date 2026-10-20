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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AnnotatedClientRequestBinder} implementation for {@link RequestAttribute}.
 */
public class AttributeClientRequestBinder implements AnnotatedClientRequestBinder<RequestAttribute> {
    @Override
    public void bind(MethodInvocationContext<Object, Object> context,
                     ClientRequestUriContext uriContext,
                     MutableHttpRequest<?> request) {
        // The attributes of the method are kept by the method, unless their placeholders may resolve to another value
        // on every request (e.g. ${random.uuid})
        List<Object> attributes = (context.hasPropertyExpressions() ? Attributes.of(context) : context.getCachedValue(Attributes.class, Attributes::of))
            .namesAndValues();
        for (int i = 0; i < attributes.size(); i += 2) {
            request.setAttribute((String) attributes.get(i), attributes.get(i + 1));
        }
    }

    @Override
    public Class<RequestAttribute> getAnnotationType() {
        return RequestAttribute.class;
    }

    /**
     * The request attributes of a client method, as name/value pairs, skipping an attribute without a name or a value.
     *
     * @param namesAndValues The names and values
     */
    private record Attributes(List<Object> namesAndValues) {

        static Attributes of(ExecutableMethod<?, ?> method) {
            List<AnnotationValue<RequestAttribute>> attributeAnnotations = method.getAnnotationValuesByType(RequestAttribute.class);
            List<Object> namesAndValues = new ArrayList<>(attributeAnnotations.size() * 2);
            for (AnnotationValue<RequestAttribute> attributeAnnotation : attributeAnnotations) {
                String attributeName = attributeAnnotation.stringValue("name").orElse(null);
                Object attributeValue = attributeAnnotation.getValue(Object.class).orElse(null);
                if (StringUtils.isNotEmpty(attributeName) && attributeValue != null) {
                    namesAndValues.add(attributeName);
                    namesAndValues.add(attributeValue);
                }
            }
            return new Attributes(List.copyOf(namesAndValues));
        }
    }

}
