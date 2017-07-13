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
import org.particleframework.bind.annotation.AnnotatedArgumentBinder;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.ConvertibleValues;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.binding.annotation.Cookie;
import org.particleframework.inject.Argument;

import java.util.Optional;

/**
 * Binds a value from a {@link org.particleframework.http.cookie.Cookie}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CookieAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Cookie, T, HttpRequest> implements AnnotatedRequestArgumentBinder<Cookie, T> {
    public CookieAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public Class<Cookie> annotationType() {
        return Cookie.class;
    }

    @Override
    public Optional<T> bind(Argument<T> argument, HttpRequest source) {
        ConvertibleValues<org.particleframework.http.cookie.Cookie> parameters = source.getCookies();
        Cookie annotation = argument.getAnnotation(Cookie.class);
        String parameterName = annotation.value();
        return doBind(argument, parameters, parameterName);
    }

    @Override
    protected String getFallbackFormat(Argument argument) {
        return NameUtils.hyphenate(argument.getName());
    }
}
