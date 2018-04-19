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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an argument to a constructor or method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgument<T> implements Argument<T> {
    private final Class<T> type;
    private final String name;
    private final AnnotatedElement annotatedElement;
    private final Map<String, Argument<?>> typeParameters;
    private final Argument[] typeParameterArray;
    private Annotation qualifier;

    DefaultArgument(Class<T> type, String name, Annotation qualifier, Annotation[] annotations, Argument... genericTypes) {
        this.type = type;
        this.name = name;
        this.annotatedElement = createInternalElement(annotations);
        this.qualifier = qualifier;
        this.typeParameters = initializeTypeParameters(genericTypes);
        this.typeParameterArray = genericTypes;
    }

    DefaultArgument(Class<T> type, String name, Annotation qualifier, Argument... genericTypes) {
        this.type = type;
        this.name = name;
        this.annotatedElement = AnnotationUtil.EMPTY_ANNOTATED_ELEMENT;
        this.qualifier = qualifier;
        this.typeParameters = initializeTypeParameters(genericTypes);
        this.typeParameterArray = genericTypes;
    }


    DefaultArgument(Class<T> type, String name, AnnotationMetadata annotationMetadata, Argument... genericTypes) {
        this.type = type;
        this.name = name;
        this.annotatedElement = annotationMetadata != null ? annotationMetadata : AnnotationUtil.EMPTY_ANNOTATED_ELEMENT;
        if(annotationMetadata != null) {
            this.qualifier = annotationMetadata.getAnnotationTypeByStereotype("javax.inject.Qualifier")
                                               .map(annotationMetadata::getAnnotation)
                                               .orElse(null);
        }
        this.typeParameters = initializeTypeParameters(genericTypes);
        this.typeParameterArray = genericTypes;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if(annotatedElement instanceof AnnotationMetadata) {
            return (AnnotationMetadata) annotatedElement;
        }
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public Optional<Argument<?>> getFirstTypeVariable() {
        if (!typeParameters.isEmpty()) {
            return typeParameters.values().stream().findFirst();
        }
        return Optional.empty();
    }

    @Override
    public Argument[] getTypeParameters() {
        return typeParameterArray;
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return this.typeParameters;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Annotation getQualifier() {
        if(this.qualifier != null) {
            return this.qualifier;
        }
        else {
            this.qualifier = AnnotationUtil.findAnnotationWithStereoType("javax.inject.Qualifier", getAnnotations()).orElse(null);
            return qualifier;
        }
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return new AnnotatedElement[]{annotatedElement};
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return type.getSimpleName() + " " + name;
    }

    @Override
    public boolean equalsType(Argument<?> o) {
        if (this == o) return true;
        if (o == null) return false;
        return Objects.equals(type, o.getType()) &&
            Objects.equals(typeParameters, o.getTypeVariables());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultArgument<?> that = (DefaultArgument<?>) o;
        return Objects.equals(type, that.type) &&
            Objects.equals(name, that.name) &&
            Objects.equals(typeParameters, that.typeParameters);
    }

    @Override
    public int typeHashCode() {
        return Objects.hash(type, typeParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, typeParameters);
    }

    private Map<String, Argument<?>> initializeTypeParameters(Argument[] genericTypes) {
        Map<String, Argument<?>> typeParameters;
        if (genericTypes != null && genericTypes.length > 0) {
            typeParameters = new LinkedHashMap<>(genericTypes.length);
            for (Argument genericType : genericTypes) {
                typeParameters.put(genericType.getName(), genericType);
            }
        } else {
            typeParameters = Collections.emptyMap();
        }
        return typeParameters;
    }

    private AnnotatedElement createInternalElement(Annotation[] annotations) {
        return new AnnotatedElement() {
            @Override
            public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
                return AnnotationUtil.findAnnotation(annotations, annotationClass).orElse(null);
            }

            @Override
            public Annotation[] getAnnotations() {
                return annotations;
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                return annotations;
            }
        };
    }
}
