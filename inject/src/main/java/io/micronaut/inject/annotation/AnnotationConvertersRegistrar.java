/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.reflect.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registers annotation converters.
 *
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Internal
public final class AnnotationConvertersRegistrar implements TypeConverterRegistrar {

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(io.micronaut.core.annotation.AnnotationValue.class, Annotation.class, (object, targetType, context) -> {
            @SuppressWarnings("unchecked") Class<? extends Annotation> aClass =
                (Class<? extends Annotation>) ClassUtils.forName(object.getAnnotationName(), targetType.getClassLoader()).orElse(null);

            if (aClass != null) {
                return Optional.of(AnnotationMetadataSupport.buildAnnotation(aClass, object));
            }
            return Optional.empty();
        });

        conversionService.addConverter(io.micronaut.core.annotation.AnnotationValue[].class, Object[].class, (object, targetType, context) -> {
            List<Annotation> result = new ArrayList<>();
            Class<? extends Annotation> annotationClass = null;
            for (io.micronaut.core.annotation.AnnotationValue annotationValue : object) {
                if (annotationClass == null) {
                    // all annotations will be on the same type
                    @SuppressWarnings("unchecked") Class<? extends Annotation> aClass =
                        (Class<? extends Annotation>) ClassUtils.forName(annotationValue.getAnnotationName(), targetType.getClassLoader()).orElse(null);
                    if (aClass == null) {
                        break;
                    }
                    annotationClass = aClass;
                }
                Annotation annotation = AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);
                result.add(annotation);
            }
            if (!result.isEmpty()) {
                return Optional.of(result.toArray((Object[]) Array.newInstance(annotationClass, result.size())));
            }
            return Optional.empty();
        });
    }

}
