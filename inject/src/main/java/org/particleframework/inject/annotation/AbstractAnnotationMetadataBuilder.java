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
package org.particleframework.inject.annotation;

import org.particleframework.context.annotation.AliasFor;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.core.value.OptionalValues;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.*;

/**
 * An abstract implementation that builds {@link AnnotationMetadata}
 *
 * @param <T> The element type
 * @param <A> The annotation type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotationMetadataBuilder<T, A> {
    private static final List<String> INTERNAL_ANNOTATION_NAMES = Arrays.asList(
            Retention.class.getName(),
            Repeatable.class.getName(),
            Documented.class.getName(),
            Target.class.getName()
    );

    /**
     * Build the meta data for the given element
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata build(T element) {
        DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
        List<T> hierarchy = buildHierarchy(element);
        for (T currentElement : hierarchy) {
            List<? extends A> annotationHierarchy =
                    getAnnotationsForType(currentElement);

            if(annotationHierarchy.isEmpty()) continue;

            Map<String, Map<CharSequence, Object>> annotationValues = new LinkedHashMap<>();

            for (A a : annotationHierarchy) {
                String annotationName = getAnnotationTypeName(a);
                if(INTERNAL_ANNOTATION_NAMES.contains(annotationName)) continue;

                Map<String, Map<CharSequence, Object>> stereotypes = new LinkedHashMap<>();
                List<A> stereotypeHierarchy = new ArrayList<>();
                buildStereotypeHierarchy(getTypeForAnnotation(a), stereotypeHierarchy);
                Collections.reverse(stereotypeHierarchy);
                for (A stereotype : stereotypeHierarchy) {
                    populateAnnotationData(stereotype, stereotypes, stereotypes);
                }

                Map<CharSequence, Object> values = populateAnnotationData(a, annotationValues, stereotypes);

                if(currentElement == element) {
                    annotationMetadata.addDeclaredAnnotation(annotationName, values);
                    if(!stereotypes.isEmpty()) {
                        for (Map.Entry<String, Map<CharSequence, Object>> stereotype : stereotypes.entrySet()) {
                            annotationMetadata.addDeclaredStereotype(annotationName, stereotype.getKey(), stereotype.getValue());
                        }
                    }
                }
                else {
                    annotationMetadata.addAnnotation(annotationName, values);
                    if(!stereotypes.isEmpty()) {
                        for (Map.Entry<String, Map<CharSequence, Object>> stereotype : stereotypes.entrySet()) {
                            annotationMetadata.addStereotype(annotationName, stereotype.getKey(), stereotype.getValue());
                        }
                    }
                }
            }

        }
        return annotationMetadata;
    }


    /**
     * Get the type of the given annotation
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
     * @param element The type element
     * @return The annotations
     */
    protected abstract List<? extends A> getAnnotationsForType(T element);


    /**
     * Build the type hierarchy for the given element
     *
     * @param element The element
     * @return The type hierarchy
     */
    protected abstract List<T> buildHierarchy(T element);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map
     * @param memberName The member
     * @param annotationValue The value
     * @param annotationValues The values to populate
     */
    protected abstract void readAnnotationValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues);

    /**
     * Read the raw annotation values from the given annoatation
     *
     * @param annotationMirror The annotation
     * @return The values
     */
    protected abstract Map<? extends T, ?> readAnnotationValues(A annotationMirror);

    /**
     * Resolve the annotations values from the given member for the given type
     * @param member The member
     * @param annotationType The type
     * @return The values
     */
    protected abstract OptionalValues<?> getAnnotationValues(T member, Class<?> annotationType);

    /**
     * Read the name of an annotation member
     * @param member The member
     * @return The name
     */
    protected abstract String getAnnotationMemberName(T member);

    /**
     * Populate the annotation data for the given annotation
     * @param annotationMirror The annotation
     * @param annotationData The annotation data
     */
    protected Map<CharSequence, Object> populateAnnotationData(A annotationMirror, Map<String, Map<CharSequence, Object>> annotationData, Map<String, Map<CharSequence, Object>> stereotypes) {
        String annotationName = getAnnotationTypeName(annotationMirror);
        Map<? extends T, ?> elementValues = readAnnotationValues(annotationMirror);
        if(CollectionUtils.isEmpty(elementValues)) {
            if(!annotationData.containsKey(annotationName)) {
                annotationData.put(annotationName, Collections.emptyMap());
            }
        }
        else {
            Map<CharSequence, Object> annotationValues = resolveAnnotationData(annotationData, annotationName, elementValues);
            for (Map.Entry<? extends T, ?> entry : elementValues.entrySet()) {
                T member = entry.getKey();
                OptionalValues<?> values = getAnnotationValues(member, AliasFor.class);
                Optional<?> aliasAnnotation = values.get("annotation");
                Object annotationValue = entry.getValue();
                if(aliasAnnotation.isPresent()) {
                    Optional<?> aliasMember = values.get("member");
                    if(aliasMember.isPresent()) {
                        Map<CharSequence, Object> data = resolveAnnotationData(stereotypes, aliasAnnotation.get().toString(), elementValues);
                        readAnnotationValues(aliasMember.get().toString(), annotationValue, data);
                    }
                }
                else {
                    Optional<?> aliasMember = values.get("member");
                    if(aliasMember.isPresent()) {
                        String aliasedNamed = aliasMember.get().toString();
                        readAnnotationValues(aliasedNamed, annotationValue, annotationValues);
                    }
                }
                readAnnotationValues(getAnnotationMemberName(member), annotationValue, annotationValues);
            }
            return annotationValues;
        }
        return Collections.emptyMap();
    }

    private Map<CharSequence, Object> resolveAnnotationData(Map<String, Map<CharSequence, Object>> annotationData, String annotationName, Map<? extends T, ?> elementValues) {
        return annotationData.computeIfAbsent(annotationName, s -> new LinkedHashMap<>(elementValues.size()));
    }


    private void buildStereotypeHierarchy(T element, List<A> hierarchy) {
        List<? extends A> annotationMirrors = getAnnotationsForType(element);
        for (A annotationMirror : annotationMirrors) {

            String annotationName = getAnnotationTypeName(annotationMirror);
            if(!INTERNAL_ANNOTATION_NAMES.contains(annotationName)) {
                T annotationType = getTypeForAnnotation(annotationMirror);
                hierarchy.add(annotationMirror);
                buildStereotypeHierarchy(annotationType, hierarchy);
            }
        }
    }


}
