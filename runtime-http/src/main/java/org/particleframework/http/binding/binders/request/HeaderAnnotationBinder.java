/*
 * Copyright 2017 original authors
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
package org.particleframework.http.binding.binders.request;

import org.particleframework.bind.annotation.AbstractAnnotatedArgumentBinder;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.ConvertibleMultiValues;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.binding.annotation.Header;
import org.particleframework.core.type.Argument;

import java.util.Optional;

/**
 * An {@link org.particleframework.bind.annotation.AnnotatedArgumentBinder} implementation that uses the {@link Header} annotation
 * to trigger binding from an HTTP header
 *
 *
 * @see HttpHeaders
 * @author Graeme Rocher
 * @since 1.0
 */
public class HeaderAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Header, T, HttpRequest> implements AnnotatedRequestArgumentBinder<Header, T> {

    public HeaderAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Optional<T> bind(Argument<T> argument, HttpRequest source) {
        ConvertibleMultiValues<String> parameters = source.getHeaders();
        Header annotation = argument.getAnnotation(Header.class);
        String parameterName = annotation.value();
        return doBind(argument, parameters, parameterName, source.getLocale(), source.getCharacterEncoding());
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
