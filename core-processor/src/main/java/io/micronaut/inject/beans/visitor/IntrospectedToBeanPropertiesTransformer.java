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
package io.micronaut.inject.beans.visitor;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

/**
 * Map values of {@link Introspected} to {@link BeanProperties}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class IntrospectedToBeanPropertiesTransformer implements TypedAnnotationTransformer<Introspected> {

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Introspected> annotation, VisitorContext visitorContext) {
        // We need to use AnnotationTransformer instead of AnnotationMapper
        // Somehow it doesn't work when the annotation is added
        Introspected.AccessKind[] accessKinds = annotation.enumValues(BeanProperties.MEMBER_ACCESS_KIND, Introspected.AccessKind.class);
        Introspected.Visibility[] visibilities = annotation.enumValues(BeanProperties.MEMBER_VISIBILITY, Introspected.Visibility.class);
        if (ArrayUtils.isEmpty(accessKinds)) {
            accessKinds = Introspected.DEFAULT_ACCESS_KIND;
        }
        if (ArrayUtils.isEmpty(visibilities)) {
            visibilities = Introspected.DEFAULT_VISIBILITY;
        }
        return Arrays.asList(
            annotation,
            AnnotationValue.builder(BeanProperties.class.getName(), RetentionPolicy.CLASS)
                .member(BeanProperties.MEMBER_ACCESS_KIND, accessKinds)
                .member(BeanProperties.MEMBER_VISIBILITY, visibilities)
                .member(BeanProperties.MEMBER_INCLUDES, annotation.stringValues(BeanProperties.MEMBER_INCLUDES))
                .member(BeanProperties.MEMBER_EXCLUDES, annotation.stringValues(BeanProperties.MEMBER_EXCLUDES))
                .member(BeanProperties.MEMBER_EXCLUDED_ANNOTATIONS, annotation.stringValues(BeanProperties.MEMBER_EXCLUDED_ANNOTATIONS))
                .build()
        );
    }

    @Override
    public Class<Introspected> annotationType() {
        return Introspected.class;
    }
}
