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
package io.micronaut.inject.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.OptionalValues;

import javax.inject.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An abstract implementation that builds {@link AnnotationMetadata}
 *
 * @param <T> The element type
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotationMetadataBuilder<T, A> {

    /**
     * Build the meta data for the given element. If the element is a method the class metadata will be included
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata build(T element) {
        DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
        AnnotationMetadata metadata = buildInternal(null, element, annotationMetadata, true);
        if(metadata.isEmpty()) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        return metadata;
    }

    /**
     * Build the meta data for the given method element excluding any class metadata
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForMethod(T element) {
        DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
        return buildInternal(null, element, annotationMetadata, false);
    }

    /**
     * Build the meta data for the given method element excluding any class metadata
     *
     * @param parent  The parent element
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(T parent, T element) {
        DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
        return buildInternal(parent, element, annotationMetadata, false);
    }

    /**
     * Get the type of the given annotation
     *
     * @param annotationMirror The annotation
     * @return The type
     */
    protected abstract T getTypeForAnnotation(A annotationMirror);

    /**
     * Get the given type of the annotation
     *
     * @param annotationMirror The annotation
     * @return The type
     */
    protected abstract String getAnnotationTypeName(A annotationMirror);

    /**
     * Obtain the annotations for the given type
     *
     * @param element The type element
     * @return The annotations
     */
    protected abstract List<? extends A> getAnnotationsForType(T element);

    /**
     * Build the type hierarchy for the given element
     *
     * @param element                The element
     * @param inheritTypeAnnotations
     * @return The type hierarchy
     */
    protected abstract List<T> buildHierarchy(T element, boolean inheritTypeAnnotations);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map
     *
     * @param memberName       The member
     * @param annotationValue  The value
     * @param annotationValues The values to populate
     */
    protected abstract void readAnnotationRawValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map
     *
     * @param memberName      The member
     * @param annotationValue The value
     */
    protected abstract Object readAnnotationValue(String memberName, Object annotationValue);

    /**
     * Read the raw annotation values from the given annoatation
     *
     * @param annotationMirror The annotation
     * @return The values
     */
    protected abstract Map<? extends T, ?> readAnnotationRawValues(A annotationMirror);

    /**
     * Resolve the annotations values from the given member for the given type
     *
     * @param member         The member
     * @param annotationType The type
     * @return The values
     */
    protected abstract OptionalValues<?> getAnnotationValues(T member, Class<?> annotationType);

    /**
     * Read the name of an annotation member
     *
     * @param member The member
     * @return The name
     */
    protected abstract String getAnnotationMemberName(T member);

    protected AnnotationValue readNestedAnnotationValue(A annotationMirror) {
        AnnotationValue av;
        Map<? extends T, ?> annotationValues = readAnnotationRawValues(annotationMirror);
        if (annotationValues.isEmpty()) {
            av = new AnnotationValue(getAnnotationTypeName(annotationMirror));
        } else {

            Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : annotationValues.entrySet()) {
                T member = entry.getKey();
                OptionalValues<?> values = getAnnotationValues(member, AliasFor.class);
                Object annotationValue = entry.getValue();
                Optional<?> aliasMember = values.get("member");
                Optional<?> aliasAnnotation = values.get("annotation");
                if (aliasMember.isPresent() && !aliasAnnotation.isPresent()) {
                    String aliasedNamed = aliasMember.get().toString();
                    readAnnotationRawValues(aliasedNamed, annotationValue, resolvedValues);
                }
                String memberName = getAnnotationMemberName(member);
                readAnnotationRawValues(memberName, annotationValue, resolvedValues);
            }
            av = new AnnotationValue(getAnnotationTypeName(annotationMirror), resolvedValues);
        }

        return av;
    }

    /**
     * Populate the annotation data for the given annotation
     *
     * @param annotationMirror The annotation
     * @param metadata         the metadata
     * @param isDeclared       Is the annotation a declared annotation
     */
    protected Map<CharSequence, Object> populateAnnotationData(
        A annotationMirror,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared) {
        String annotationName = getAnnotationTypeName(annotationMirror);
        Set<String> parentAnnotations = CollectionUtils.setOf(annotationName);
        Map<? extends T, ?> elementValues = readAnnotationRawValues(annotationMirror);
        if (CollectionUtils.isEmpty(elementValues)) {
            return Collections.emptyMap();
        } else {
            Map<CharSequence, Object> annotationValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : elementValues.entrySet()) {
                T member = entry.getKey();
                OptionalValues<?> values = getAnnotationValues(member, AliasFor.class);
                Optional<?> aliasAnnotation = values.get("annotation");
                Object annotationValue = entry.getValue();
                if (aliasAnnotation.isPresent()) {
                    Optional<?> aliasMember = values.get("member");
                    if (aliasMember.isPresent()) {
                        String aliasedAnnotationName = aliasAnnotation.get().toString();
                        String aliasedMemberName = aliasMember.get().toString();
                        Object v = readAnnotationValue(aliasedMemberName, annotationValue);
                        if (v != null) {


                            if (isDeclared) {
                                metadata.addDeclaredStereotype(
                                    parentAnnotations,
                                    aliasedAnnotationName,
                                    Collections.singletonMap(aliasedMemberName, v)
                                );
                            } else {
                                metadata.addStereotype(
                                    parentAnnotations,
                                    aliasedAnnotationName,
                                    Collections.singletonMap(aliasedMemberName, v)
                                );
                            }
                        }
                    }
                } else {
                    Optional<?> aliasMember = values.get("member");
                    if (aliasMember.isPresent()) {
                        String aliasedNamed = aliasMember.get().toString();
                        Object v = readAnnotationValue(aliasedNamed, annotationValue);
                        if (v != null) {
                            annotationValues.put(aliasedNamed, v);
                        }
                        readAnnotationRawValues(aliasedNamed, annotationValue, annotationValues);
                    }
                }
                readAnnotationRawValues(getAnnotationMemberName(member), annotationValue, annotationValues);
            }
            return annotationValues;
        }
    }

    private AnnotationMetadata buildInternal(T parent, T element, DefaultAnnotationMetadata annotationMetadata, boolean inheritTypeAnnotations) {
        List<T> hierarchy = buildHierarchy(element, inheritTypeAnnotations);
        if (parent != null) {
            hierarchy.add(0, parent);
        }
        Collections.reverse(hierarchy);
        for (T currentElement : hierarchy) {
            List<? extends A> annotationHierarchy = getAnnotationsForType(currentElement);

            if (annotationHierarchy.isEmpty()) continue;
            boolean isDeclared = currentElement == element;

            for (A annotationMirror : annotationHierarchy) {
                String annotationName = getAnnotationTypeName(annotationMirror);
                if (AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)) continue;

                Map<CharSequence, Object> annotationValues = populateAnnotationData(annotationMirror, annotationMetadata, isDeclared);

                if (isDeclared) {
                    annotationMetadata.addDeclaredAnnotation(annotationName, annotationValues);

                } else {
                    annotationMetadata.addAnnotation(annotationName, annotationValues);
                }
            }
            for (A annotationMirror : annotationHierarchy) {
                String parentAnnotationName = getAnnotationTypeName(annotationMirror);
                T annotationType = getTypeForAnnotation(annotationMirror);
                buildStereotypeHierarchy(
                    CollectionUtils.setOf(parentAnnotationName),
                    annotationType,
                    annotationMetadata,
                    isDeclared
                );
            }

        }
        if (!annotationMetadata.hasDeclaredStereotype(Scope.class) && annotationMetadata.hasDeclaredStereotype(DefaultScope.class)) {
            Optional<String> value = annotationMetadata.getValue(DefaultScope.class, String.class);
            value.ifPresent(name -> annotationMetadata.addDeclaredAnnotation(name, Collections.emptyMap()));
        }
        return annotationMetadata;
    }


    private void buildStereotypeHierarchy(Set<String> parents, T element, DefaultAnnotationMetadata metadata, boolean isDeclared) {
        List<? extends A> annotationMirrors = getAnnotationsForType(element);
        if (!annotationMirrors.isEmpty()) {

            // first add the top level annotations
            List<A> topLevel = new ArrayList<>();
            for (A annotationMirror : annotationMirrors) {

                String annotationName = getAnnotationTypeName(annotationMirror);
                if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)) {
                    topLevel.add(annotationMirror);

                    Map<CharSequence, Object> data = populateAnnotationData(annotationMirror, metadata, isDeclared);
                    if (isDeclared) {
                        metadata.addDeclaredStereotype(parents, annotationName, data);
                    } else {
                        metadata.addStereotype(parents, annotationName, data);
                    }
                }
            }
            // now add meta annotations
            for (A annotationMirror : topLevel) {
                T typeForAnnotation = getTypeForAnnotation(annotationMirror);
                String annotationTypeName = getAnnotationTypeName(annotationMirror);
                Set<String> stereoTypeParents = CollectionUtils.setOf(annotationTypeName);
                stereoTypeParents.addAll(parents);
                buildStereotypeHierarchy(stereoTypeParents, typeForAnnotation, metadata, isDeclared);
            }
        }
    }
}
