/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
import java.util.stream.Stream;

/**
 * A {@link Qualifier} that uses {@link AnnotationMetadata}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationMetadataQualifier<T> extends NameQualifier<T> {

    private final AnnotationMetadata annotationMetadata;

    AnnotationMetadataQualifier(AnnotationMetadata metadata, String name) {
        super(name);
        this.annotationMetadata = metadata;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        String name;
        if (Named.class.getName().equals(getName())) {
            String v = annotationMetadata.getValue(Named.class, String.class).orElse(null);
            if (StringUtils.isNotEmpty(v)) {
                name = Character.toUpperCase(v.charAt(0)) + v.substring(1);
            } else {
                name = getName();
            }

        } else {
            name = getName();
        }

        return reduceByAnnotation(beanType, candidates, name);
    }
}
