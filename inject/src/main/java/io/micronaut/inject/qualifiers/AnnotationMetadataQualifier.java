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
package io.micronaut.inject.qualifiers;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.context.Qualifier} that uses {@link AnnotationMetadata}.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationMetadataQualifier<T> extends NameQualifier<T> {

    private final AnnotationMetadata annotationMetadata;
    private final Class<? extends Annotation> annotationType;
    private final String qualifiedName;

    /**
     * @param metadata The annotation metadata
     * @param name     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, String name) {
        super(name);
        this.annotationMetadata = metadata;
        this.annotationType = null;
        this.qualifiedName = null;
    }

    /**
     * @param metadata The annotation metadata
     * @param annotationType     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, Class<? extends Annotation> annotationType) {
        super(annotationType.getSimpleName());
        this.annotationMetadata = metadata;
        this.annotationType = annotationType;
        this.qualifiedName = annotationType.getName();
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        String name;
        String v = annotationMetadata.stringValue(Named.class).orElse(null);
        if (StringUtils.isNotEmpty(v)) {
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);
            return reduceByName(beanType, candidates, name);
        } else {
            name = getName();
            return reduceByAnnotation(beanType, candidates, name, qualifiedName);
        }

    }

    @Override
    public String toString() {
        return annotationType == null ? super.toString() : "@" + annotationType.getSimpleName();
    }
}
