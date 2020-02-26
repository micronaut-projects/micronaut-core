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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Header;

/**
 * An {@link io.micronaut.core.bind.annotation.AnnotatedArgumentBinder} implementation that uses the {@link Header}
 * annotation to trigger binding from an HTTP header.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @see io.micronaut.http.HttpHeaders
 * @since 1.0
 */
public class HeaderAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Header, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<Header, T> {

    /**
     * @param conversionService The conversion service
     */
    public HeaderAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> argument, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getHeaders();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        String parameterName = annotationMetadata.stringValue(Header.class).orElse(argument.getArgument().getName());
        return doBind(argument, parameters, parameterName);
    }

    @Override
    public Class<Header> getAnnotationType() {
        return Header.class;
    }

    @Override
    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(NameUtils.capitalize(argument.getName()), false);
    }
}
