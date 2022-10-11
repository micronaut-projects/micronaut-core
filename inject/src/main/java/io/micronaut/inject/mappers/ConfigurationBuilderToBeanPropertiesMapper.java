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
package io.micronaut.inject.mappers;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Map values of {@link ConfigurationBuilder} to {@link BeanProperties}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class ConfigurationBuilderToBeanPropertiesMapper implements TypedAnnotationMapper<ConfigurationBuilder> {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<ConfigurationBuilder> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(BeanProperties.class)
                // Configuration properties also includes fields
                .member(BeanProperties.MEMBER_ACCESS_KIND, new BeanProperties.AccessKind[]{BeanProperties.AccessKind.METHOD})
                .member(BeanProperties.MEMBER_VISIBILITY, BeanProperties.Visibility.DEFAULT)
                .member(BeanProperties.MEMBER_INCLUDES, annotation.stringValues(BeanProperties.MEMBER_INCLUDES))
                .member(BeanProperties.MEMBER_EXCLUDES, annotation.stringValues(BeanProperties.MEMBER_EXCLUDES))
                .member(BeanProperties.MEMBER_ALLOW_WRITE_WITH_ZERO_ARGS, annotation.booleanValue("allowZeroArgs").orElse(false))
                .member(BeanProperties.MEMBER_ALLOW_WRITE_WITH_MULTIPLE_ARGS, true)
                .build()
        );
    }

    @Override
    public Class<ConfigurationBuilder> annotationType() {
        return ConfigurationBuilder.class;
    }
}
