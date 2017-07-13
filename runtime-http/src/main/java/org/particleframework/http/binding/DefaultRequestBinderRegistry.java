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
package org.particleframework.http.binding;

import org.particleframework.bind.ArgumentBinder;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.bind.annotation.Bindable;
import org.particleframework.http.binding.binders.request.AnnotatedRequestArgumentBinder;
import org.particleframework.http.binding.binders.request.ParameterAnnotationBinder;
import org.particleframework.http.binding.binders.request.RequestArgumentBinder;
import org.particleframework.inject.Argument;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultRequestBinderRegistry implements RequestBinderRegistry {

    private final Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation = new LinkedHashMap<>();
    private final ConversionService<?> conversionService;

    public DefaultRequestBinderRegistry(ConversionService<?> conversionService, RequestArgumentBinder...binders) {
        this.conversionService = conversionService;
        for (RequestArgumentBinder binder : binders) {
            if(binder instanceof AnnotatedRequestArgumentBinder) {
                AnnotatedRequestArgumentBinder<?,?> annotatedRequestArgumentBinder = (AnnotatedRequestArgumentBinder) binder;
                byAnnotation.put(annotatedRequestArgumentBinder.annotationType(), annotatedRequestArgumentBinder);
            }
        }
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest>> findArgumentBinder(Argument<T> argument, HttpRequest source) {
        Annotation annotation = argument.findAnnotation(Bindable.class);
        if(annotation != null) {

            RequestArgumentBinder<T> binder = byAnnotation.get(annotation.annotationType());
            if(binder != null) {
                return Optional.of(binder);
            }
        }
        return Optional.of(new ParameterAnnotationBinder<>(conversionService));
    }
}
