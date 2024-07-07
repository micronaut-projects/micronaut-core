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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

/**
 * Map values of {@link Introspected} to {@link BeanProperties}, because {@link Introspected} module doesn't depend on the inject module.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class IntrospectedToBeanPropertiesTransformer implements TypedAnnotationTransformer<Introspected> {

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Introspected> annotation, VisitorContext visitorContext) {
        // Mapping bellow should only set the members when the value is different then the default one
        Introspected.AccessKind[] accessKinds = annotation.enumValues(BeanProperties.MEMBER_ACCESS_KIND, Introspected.AccessKind.class);
        AnnotationValueBuilder<Annotation> beanPropertiesBuilder = AnnotationValue.builder(BeanProperties.class.getName(), RetentionPolicy.CLASS);
        if (accessKinds.length != 0 && !Arrays.equals(accessKinds, Introspected.DEFAULT_ACCESS_KIND)) {
            beanPropertiesBuilder = beanPropertiesBuilder.member(BeanProperties.MEMBER_ACCESS_KIND, Arrays.stream(accessKinds).map(Enum::name).toArray(String[]::new));
        }
        Introspected.Visibility[] visibilities = annotation.enumValues(BeanProperties.MEMBER_VISIBILITY, Introspected.Visibility.class);
        if (visibilities.length != 0 && !Arrays.equals(visibilities, Introspected.DEFAULT_VISIBILITY)) {
            beanPropertiesBuilder = beanPropertiesBuilder.member(BeanProperties.MEMBER_VISIBILITY, Arrays.stream(visibilities).map(Enum::name).toArray(String[]::new));
        }
        String[] includes = annotation.stringValues(BeanProperties.MEMBER_INCLUDES);
        if (includes.length > 0) {
            beanPropertiesBuilder = beanPropertiesBuilder.member(BeanProperties.MEMBER_INCLUDES, includes);
        }
        String[] excludes = annotation.stringValues(BeanProperties.MEMBER_EXCLUDES);
        if (excludes.length > 0) {
            beanPropertiesBuilder = beanPropertiesBuilder.member(BeanProperties.MEMBER_EXCLUDES, excludes);
        }
        String[] excludedAnnotations = annotation.stringValues(BeanProperties.MEMBER_EXCLUDED_ANNOTATIONS);
        if (excludedAnnotations.length > 0) {
            beanPropertiesBuilder = beanPropertiesBuilder.member(BeanProperties.MEMBER_EXCLUDED_ANNOTATIONS, excludedAnnotations);
        }
        return List.of(
            annotation.mutate().stereotype(beanPropertiesBuilder.build()).build()
        );
    }

    @Override
    public Class<Introspected> annotationType() {
        return Introspected.class;
    }
}
