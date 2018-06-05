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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link AnnotationMetadata}.
 *
 * <p>
 * NOTE: Although required to be public This is an internal class and should not be referenced directly in user code
 * </p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultAnnotationMetadata extends AbstractAnnotationMetadata implements AnnotationMetadata, AnnotatedElement, Cloneable {

    static {
        ConversionService.SHARED.addConverter(AnnotationValue.class, Annotation.class, (TypeConverter<AnnotationValue, Annotation>) (object, targetType, context) -> {
            Optional<Class> annotationClass = ClassUtils.forName(object.getAnnotationName(), targetType.getClassLoader());
            return annotationClass.map(aClass -> AnnotationMetadataSupport.buildAnnotation(aClass, ConvertibleValues.of(object.getValues())));
        });

        ConversionService.SHARED.addConverter(AnnotationValue[].class, Object[].class, (TypeConverter<AnnotationValue[], Object[]>) (object, targetType, context) -> {
            List result = new ArrayList();
            Class annotationClass = null;
            for (AnnotationValue annotationValue : object) {
                if (annotationClass == null) {
                    // all annotations will be on the same type
                    Optional<Class> aClass = ClassUtils.forName(annotationValue.getAnnotationName(), targetType.getClassLoader());
                    if (!aClass.isPresent()) {
                        break;
                    }
                    annotationClass = aClass.get();
                }
                Annotation annotation = AnnotationMetadataSupport.buildAnnotation(annotationClass, ConvertibleValues.of(annotationValue.getValues()));
                result.add(annotation);
            }
            if (!result.isEmpty()) {
                return Optional.of(result.toArray((Object[]) Array.newInstance(annotationClass, result.size())));
            }
            return Optional.empty();
        });
    }

    Map<String, Map<CharSequence, Object>> declaredAnnotations;
    Map<String, Map<CharSequence, Object>> allAnnotations;
    Map<String, Map<CharSequence, Object>> declaredStereotypes;
    Map<String, Map<CharSequence, Object>> allStereotypes;
    Map<String, List<String>> annotationsByStereotype;

    /**
     * Constructs empty annotation metadata.
     */
    @Internal
    protected DefaultAnnotationMetadata() {
    }

    /**
     * This constructor is designed to be used by compile time produced subclasses.
     *
     * @param declaredAnnotations     The directly declared annotations
     * @param declaredStereotypes     The directly declared stereotypes
     * @param allStereotypes          All of the stereotypes
     * @param allAnnotations          All of the annotations
     * @param annotationsByStereotype The annotations by stereotype
     */
    @Internal
    public DefaultAnnotationMetadata(
        @Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
        @Nullable Map<String, Map<CharSequence, Object>> declaredStereotypes,
        @Nullable Map<String, Map<CharSequence, Object>> allStereotypes,
        @Nullable Map<String, Map<CharSequence, Object>> allAnnotations,
        @Nullable Map<String, List<String>> annotationsByStereotype) {
        super(declaredAnnotations, allAnnotations);
        this.declaredAnnotations = declaredAnnotations;
        this.declaredStereotypes = declaredStereotypes;
        this.allStereotypes = allStereotypes;
        this.allAnnotations = allAnnotations;
        this.annotationsByStereotype = annotationsByStereotype;
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        Map<String, Object> defaultValues = AnnotationMetadataSupport.getDefaultValues(annotation);
        if (defaultValues.containsKey(member)) {
            return ConversionService.SHARED.convert(defaultValues.get(member), requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        Map<String, Object> defaultValues = AnnotationMetadataSupport.getDefaultValues(annotation);
        if (defaultValues.containsKey(member)) {
            return ConversionService.SHARED.convert(defaultValues.get(member), requiredType);
        }
        return Optional.empty();
    }

    @Override
    public boolean isEmpty() {
        return allAnnotations == null || allAnnotations.isEmpty();
    }

    @Override
    public boolean hasDeclaredAnnotation(String annotation) {
        return declaredAnnotations != null && StringUtils.isNotEmpty(annotation) && declaredAnnotations.containsKey(annotation);
    }

    @Override
    public boolean hasAnnotation(String annotation) {
        return hasDeclaredAnnotation(annotation) || (allAnnotations != null && StringUtils.isNotEmpty(annotation) && allAnnotations.keySet().contains(annotation));
    }

    @Override
    public boolean hasStereotype(String annotation) {
        return hasAnnotation(annotation) || (allStereotypes != null && StringUtils.isNotEmpty(annotation) && allStereotypes.keySet().contains(annotation));
    }

    @Override
    public boolean hasDeclaredStereotype(String annotation) {
        return hasDeclaredAnnotation(annotation) || (declaredStereotypes != null && StringUtils.isNotEmpty(annotation) && declaredStereotypes.containsKey(annotation));
    }

    @Override
    public List<String> getAnnotationNamesByStereotype(String stereotype) {
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                return Collections.unmodifiableList(annotations);
            }
        }
        if (allAnnotations != null && allAnnotations.containsKey(stereotype)) {
            return StringUtils.internListOf(stereotype);
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return StringUtils.internListOf(stereotype);
        }
        return Collections.emptyList();
    }

    @Override
    public Set<String> getAnnotationNames() {
        if (allAnnotations != null) {
            return allAnnotations.keySet();
        }
        return Collections.emptySet();
    }

    @Override
    public Set<String> getDeclaredAnnotationNames() {
        if (declaredAnnotations != null) {
            return declaredAnnotations.keySet();
        }
        return Collections.emptySet();
    }

    @Override
    public List<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        if (annotationsByStereotype != null) {
            List<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                annotations = new ArrayList<>(annotations);
                if (declaredAnnotations != null) {
                    annotations.removeIf(s -> !declaredAnnotations.containsKey(s));
                    return Collections.unmodifiableList(annotations);
                } else {
                    // no declared
                    return Collections.emptyList();
                }
            }
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return StringUtils.internListOf(stereotype);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("Duplicates")
    @Override
    public ConvertibleValues<Object> getValues(String annotation) {
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                return convertibleValuesOf(values);
            } else if (allStereotypes != null) {
                return convertibleValuesOf(allStereotypes.get(annotation));
            }
        }
        return ConvertibleValuesMap.empty();
    }

    @SuppressWarnings("Duplicates")
    @Override
    public ConvertibleValues<Object> getDeclaredValues(String annotation) {
        if (declaredAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = declaredAnnotations.get(annotation);
            if (values != null) {
                return convertibleValuesOf(values);
            } else if (declaredStereotypes != null) {
                return convertibleValuesOf(declaredStereotypes.get(annotation));
            }
        }
        return ConvertibleValuesMap.empty();
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                return OptionalValues.of(valueType, values);
            } else {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    return OptionalValues.of(valueType, values);
                }
            }
        }
        return OptionalValues.empty();
    }

    @Override
    public AnnotationMetadata clone() {
        return new DefaultAnnotationMetadata(
            declaredAnnotations != null ? new HashMap<>(declaredAnnotations) : null,
            declaredStereotypes != null ? new HashMap<>(declaredStereotypes) : null,
            allStereotypes != null ? new HashMap<>(allStereotypes) : null,
            allAnnotations != null ? new HashMap<>(allAnnotations) : null,
            annotationsByStereotype != null ? new HashMap<>(annotationsByStereotype) : null
        );
    }

    /**
     * Adds an annotation and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    @SuppressWarnings("WeakerAccess")
    protected final void addAnnotation(String annotation, Map<CharSequence, Object> values) {
        if (annotation != null) {
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
            addAnnotation(annotation, values, null, allAnnotations, false);
        }
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     */
    @SuppressWarnings("WeakerAccess")
    protected final void addStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        if (stereotype != null) {
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
            for (String parentAnnotation : parentAnnotations) {
                if (!annotationList.contains(parentAnnotation)) {
                    annotationList.add(parentAnnotation);
                }
            }
            addAnnotation(stereotype, values, null, allStereotypes, false);
        }
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing
     * values replaced.
     *
     * @param parentAnnotations The parent annotations
     * @param stereotype        The annotation
     * @param values            The values
     */
    @SuppressWarnings("WeakerAccess")
    protected final void addDeclaredStereotype(List<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        if (stereotype != null) {
            Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
            for (String parentAnnotation : parentAnnotations) {
                if (!annotationList.contains(parentAnnotation)) {
                    annotationList.add(parentAnnotation);
                }
            }
            addAnnotation(stereotype, values, declaredStereotypes, allStereotypes, true);
        }
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    protected void addDeclaredAnnotation(String annotation, Map<CharSequence, Object> values) {
        if (annotation != null) {
            Map<String, Map<CharSequence, Object>> declaredAnnotations = getDeclaredAnnotationsInternal();
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
            addAnnotation(annotation, values, declaredAnnotations, allAnnotations, true);
        }
    }

    /**
     * Dump the values.
     */
    @SuppressWarnings("unused")
    @Internal
    void dump() {
        System.out.println("declaredAnnotations = " + declaredAnnotations);
        System.out.println("declaredStereotypes = " + declaredStereotypes);
        System.out.println("allAnnotations = " + allAnnotations);
        System.out.println("allStereotypes = " + allStereotypes);
        System.out.println("annotationsByStereotype = " + annotationsByStereotype);
    }

    private void addAnnotation(String annotation,
                               Map<CharSequence, Object> values,
                               Map<String, Map<CharSequence, Object>> declaredAnnotations, Map<String,
        Map<CharSequence, Object>> allAnnotations,
                               boolean isDeclared) {
        if (isDeclared && declaredAnnotations != null) {
            putValues(annotation, values, declaredAnnotations);
        }
        putValues(annotation, values, allAnnotations);
    }

    private void putValues(String annotation, Map<CharSequence, Object> values, Map<String, Map<CharSequence, Object>> currentAnnotationValues) {
        Map<CharSequence, Object> existing = currentAnnotationValues.get(annotation);
        boolean hasValues = CollectionUtils.isNotEmpty(values);
        if (existing != null && hasValues) {
            if (existing.isEmpty()) {
                existing = new LinkedHashMap<>();
                currentAnnotationValues.put(annotation, existing);
            }
            for (CharSequence key : values.keySet()) {
                if (!existing.containsKey(key)) {
                    existing.put(key, values.get(key));
                }
            }
        } else {
            if (!hasValues) {
                existing = existing == null ? Collections.emptyMap() : existing;
            } else {
                existing = new LinkedHashMap<>(values.size());
                existing.putAll(values);
            }
            currentAnnotationValues.put(annotation, existing);
        }
    }

    private ConvertibleValues<Object> convertibleValuesOf(Map<CharSequence, Object> values) {
        return ConvertibleValues.of(values);
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getAllStereotypes() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.allStereotypes;
        if (stereotypes == null) {
            stereotypes = new HashMap<>(3);
            this.allStereotypes = stereotypes;
        }
        return stereotypes;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getDeclaredStereotypesInternal() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.declaredStereotypes;
        if (stereotypes == null) {
            stereotypes = new HashMap<>(3);
            this.declaredStereotypes = stereotypes;
        }
        return stereotypes;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getAllAnnotations() {
        Map<String, Map<CharSequence, Object>> annotations = this.allAnnotations;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.allAnnotations = annotations;
        }
        return annotations;
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, Map<CharSequence, Object>> getDeclaredAnnotationsInternal() {
        Map<String, Map<CharSequence, Object>> annotations = this.declaredAnnotations;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.declaredAnnotations = annotations;
        }
        return annotations;
    }

    private List<String> getAnnotationsByStereotypeInternal(String stereotype) {
        return getAnnotationsByStereotypeInternal().computeIfAbsent(stereotype, s -> new ArrayList<>());
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, List<String>> getAnnotationsByStereotypeInternal() {
        Map<String, List<String>> annotations = this.annotationsByStereotype;
        if (annotations == null) {
            annotations = new HashMap<>(3);
            this.annotationsByStereotype = annotations;
        }
        return annotations;
    }

    /**
     * <p>Sets a member of the given {@link AnnotationMetadata} return a new annotation metadata instance without
     * mutating the existing.</p>
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param annotationMetadata The metadata
     * @param annotationName     The annotation name
     * @param member             The member
     * @param value              The value
     * @return The metadata
     */
    @Internal
    public static AnnotationMetadata mutateMember(
        AnnotationMetadata annotationMetadata,
        String annotationName,
        String member,
        Object value) {

        return mutateMember(annotationMetadata, annotationName, Collections.singletonMap(member, value));
    }

    /**
     * <p>Sets a member of the given {@link AnnotationMetadata} return a new annotation metadata instance without
     * mutating the existing.</p>
     *
     * <p>WARNING: for internal use only be the framework</p>
     *
     * @param annotationMetadata The metadata
     * @param annotationName     The annotation name
     * @param members            The key/value set of members and values
     * @return The metadata
     */
    @Internal
    public static AnnotationMetadata mutateMember(
            AnnotationMetadata annotationMetadata,
            String annotationName,
            Map<CharSequence, Object> members) {
        if (StringUtils.isEmpty(annotationName)) {
            throw new IllegalArgumentException("Argument [annotationName] cannot be blank");
        }
        if (!members.isEmpty()) {
            for (Map.Entry<CharSequence, Object> entry: members.entrySet()) {
                if (StringUtils.isEmpty(entry.getKey())) {
                    throw new IllegalArgumentException("Argument [members] cannot have a blank key");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("Argument [members] cannot have a null value. Key [" + entry.getKey() + "]");
                }
            }
        }
        if (!(annotationMetadata instanceof DefaultAnnotationMetadata)) {
            return new DefaultAnnotationMetadata() {{
                addDeclaredAnnotation(annotationName, members);
            }};
        } else {
            DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) annotationMetadata;

            defaultMetadata = (DefaultAnnotationMetadata) defaultMetadata.clone();

            defaultMetadata
                    .addDeclaredAnnotation(annotationName, members);

            return defaultMetadata;
        }
    }

}
