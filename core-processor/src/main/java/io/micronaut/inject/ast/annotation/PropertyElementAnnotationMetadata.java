/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The element annotation metadata for property element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class PropertyElementAnnotationMetadata implements ElementAnnotationMetadata {

    private final io.micronaut.inject.ast.Element thisElement;
    private final List<MutableAnnotationMetadataDelegate<?>> elements;
    private final AnnotationMetadata propertyAnnotationMetadata;
    private final AnnotationMetadata propertyReadAnnotationMetadata;
    private final AnnotationMetadata propertyWriteAnnotationMetadata;

    public PropertyElementAnnotationMetadata(@NonNull
                                             io.micronaut.inject.ast.Element thisElement,
                                             @Nullable
                                             MethodElement getter,
                                             @Nullable
                                             MethodElement setter,
                                             @Nullable
                                             FieldElement field,
                                             @Nullable
                                             ParameterElement constructorParameter,
                                             boolean includeSynthetic) {

        this.thisElement = thisElement;
        List<MutableAnnotationMetadataDelegate<?>> elements = new ArrayList<>(3);
        List<MutableAnnotationMetadataDelegate<?>> readElements = new ArrayList<>(3);
        List<MutableAnnotationMetadataDelegate<?>> writeElements = new ArrayList<>(3);
        if (setter != null && (!setter.isSynthetic() || includeSynthetic)) {
            elements.add(setter.getMethodAnnotationMetadata());
            writeElements.add(setter.getMethodAnnotationMetadata());
            ParameterElement[] parameters = setter.getParameters();
            if (parameters.length > 0) {
                ParameterElement parameter = parameters[0];
                MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = parameter.getType().getTypeAnnotationMetadata();
                if (!typeAnnotationMetadata.isEmpty()) {
                    elements.add(typeAnnotationMetadata);
                    writeElements.add(typeAnnotationMetadata);
                }
            }
        }
        if (constructorParameter != null) {
            elements.add(constructorParameter);
            MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = constructorParameter.getType().getTypeAnnotationMetadata();
            if (!typeAnnotationMetadata.isEmpty()) {
                elements.add(typeAnnotationMetadata);
                writeElements.add(typeAnnotationMetadata);
            }
        }
        if (field != null && (!field.isSynthetic() || includeSynthetic)) {
            ClassElement genericFieldType = field.getGenericType();
            if (getter != null && getter.getGenericReturnType().isAssignable(Optional.class) && !genericFieldType.isAssignable(Optional.class)) {
                // The case with an Optional getter and a wrapped container field with annotations
                // We need to copy all the annotations from the field to the type argument of the optional
                // In the future we might want to support all kind of containers, not just Optional
                ClassElement wrappedArgument = getter.getGenericReturnType().getTypeArguments(Optional.class).get("T");
                if (wrappedArgument != null) {
                    AnnotationMetadata wrappedArgumentAnnotationMetadata = wrappedArgument.getTargetAnnotationMetadata();
                    AnnotationMetadata fieldAnnotationMetadata = genericFieldType.getAnnotationMetadata().getTargetAnnotationMetadata();
                    if (wrappedArgumentAnnotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata
                        && fieldAnnotationMetadata instanceof MutableAnnotationMetadata fieldMutableAnnotationMetadata) {
                        mutableAnnotationMetadata.addAnnotationMetadata(fieldMutableAnnotationMetadata);
                    }
                }
            } else {
                elements.add(field);
                writeElements.add(field);
                readElements.add(field);
                MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = field.getType().getTypeAnnotationMetadata();
                if (!typeAnnotationMetadata.isEmpty()) {
                    elements.add(typeAnnotationMetadata);
                    writeElements.add(typeAnnotationMetadata);
                    readElements.add(typeAnnotationMetadata);
                }
            }
        }

        if (getter != null && (!getter.isSynthetic() || includeSynthetic)) {
            elements.add(getter.getMethodAnnotationMetadata());
            readElements.add(getter.getMethodAnnotationMetadata());
            MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = getter.getReturnType().getTypeAnnotationMetadata();
            if (!typeAnnotationMetadata.isEmpty()) {
                elements.add(typeAnnotationMetadata);
                readElements.add(typeAnnotationMetadata);
            }
        }

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        AnnotationMetadata[] hierarchy = elements.toArray(AnnotationMetadata[]::new);
        this.propertyAnnotationMetadata =
            hierarchy.length == 1 ? hierarchy[0] : new AnnotationMetadataHierarchy(true, hierarchy);
        AnnotationMetadata[] readHierarchy = readElements.toArray(AnnotationMetadata[]::new);
        this.propertyReadAnnotationMetadata =
            readHierarchy.length == 1 ? readHierarchy[0] : new AnnotationMetadataHierarchy(true, readHierarchy);
        AnnotationMetadata[] writeHierarchy = writeElements.toArray(AnnotationMetadata[]::new);
        this.propertyWriteAnnotationMetadata =
            writeHierarchy.length == 1 ? writeHierarchy[0] : new AnnotationMetadataHierarchy(true, writeHierarchy);
        this.elements = elements;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(AnnotationValue<T> annotationValue) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.annotate(annotationValue);
        }
        return thisElement;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.annotate(annotationType, consumer);
        }
        return thisElement;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.annotate(annotationType);
        }
        return thisElement;
    }

    @Override
    public io.micronaut.inject.ast.Element annotate(String annotationType) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.annotate(annotationType);
        }
        return thisElement;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.annotate(annotationType, consumer);
        }
        return thisElement;
    }

    @Override
    public io.micronaut.inject.ast.Element removeAnnotation(String annotationType) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.removeAnnotation(annotationType);
        }
        return thisElement;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        for (MutableAnnotationMetadataDelegate<?> am : elements) {
            am.removeAnnotationIf(predicate);
        }
        return thisElement;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return propertyAnnotationMetadata;
    }

    /**
     * @return The read annotation metadata
     */
    public AnnotationMetadata getReadAnnotationMetadata() {
        return propertyReadAnnotationMetadata;
    }

    /**
     * @return The write annotation metadata
     */
    public AnnotationMetadata getWriteAnnotationMetadata() {
        return propertyWriteAnnotationMetadata;
    }
}
