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
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.util.*;

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
        ConversionService.SHARED.addConverter(io.micronaut.core.annotation.AnnotationValue.class, Annotation.class, (TypeConverter<io.micronaut.core.annotation.AnnotationValue, Annotation>) (object, targetType, context) -> {
            Optional<Class> annotationClass = ClassUtils.forName(object.getAnnotationName(), targetType.getClassLoader());
            return annotationClass.map(aClass -> AnnotationMetadataSupport.buildAnnotation(aClass, ConvertibleValues.of(object.getValues())));
        });

        ConversionService.SHARED.addConverter(io.micronaut.core.annotation.AnnotationValue[].class, Object[].class, (TypeConverter<io.micronaut.core.annotation.AnnotationValue[], Object[]>) (object, targetType, context) -> {
            List result = new ArrayList();
            Class annotationClass = null;
            for (io.micronaut.core.annotation.AnnotationValue annotationValue : object) {
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

    // should not be used in any of the read methods
    // The following fields are used only at compile time, and
    Map<String, Map<CharSequence, Object>> annotationDefaultValues;
    private Map<String, String> repeated = null;
    private Map<Class, List> annotationValuesByType = new HashMap<>();

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
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        final Repeatable repeatable = annotation.getAnnotation(Repeatable.class);
        final boolean isRepeatable = repeatable != null;
        if (isRepeatable) {
            List<? extends AnnotationValue<? extends Annotation>> values = getAnnotationValuesByType(annotation);
            if (!values.isEmpty()) {
                return values.iterator().next().get(member, requiredType);
            } else {
                return Optional.empty();
            }
        } else {
            return getValue(annotation.getName(), member, requiredType);
        }
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        Optional<T> resolved = Optional.empty();
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                resolved = ConversionService.SHARED.convert(
                        values.get(member), requiredType
                );
            } else if (allStereotypes != null) {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    resolved = ConversionService.SHARED.convert(
                            values.get(member), requiredType
                    );
                }
            }
        }

        if (!resolved.isPresent()) {
            if (hasStereotype(annotation)) {
                return getDefaultValue(annotation, member, requiredType);
            }
        }

        return resolved;
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        Map<String, Object> defaultValues = AnnotationMetadataSupport.getDefaultValues(annotation);
        if (defaultValues.containsKey(member)) {
            return ConversionService.SHARED.convert(defaultValues.get(member), requiredType);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
        if (annotationType != null) {
            return this.annotationValuesByType.computeIfAbsent(annotationType, aClass -> {
                List<AnnotationValue<T>> results = resolveAnnotationValuesByType(annotationType, allAnnotations, allStereotypes);
                if (results != null) {
                    return results;
                }
                return Collections.emptyList();
            });
        }
        return Collections.emptyList();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
        if (annotationType != null) {
            Map<String, Map<CharSequence, Object>> sourceAnnotations = this.declaredAnnotations;
            Map<String, Map<CharSequence, Object>> sourceStereotypes = this.declaredStereotypes;

            List<AnnotationValue<T>> results = resolveAnnotationValuesByType(annotationType, sourceAnnotations, sourceStereotypes);
            if (results != null) {
                return results;
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {

        if (annotationClass != null) {
            List<AnnotationValue<T>> values = getAnnotationValuesByType(annotationClass);

            return values.stream()
                        .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries.getConvertibleValues()))
                        .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        }

        //noinspection unchecked
        return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
    }

    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        if (annotationClass != null) {
            List<AnnotationValue<T>> values = getAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, entries.getConvertibleValues()))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        }

        //noinspection unchecked
        return (T[]) AnnotationUtil.ZERO_ANNOTATIONS;
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        // Note this method should never reference the "annotationDefaultValues" field, which is used only at compile time
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
    public <T extends Annotation> Optional<AnnotationValue<T>> getValues(String annotation) {
        if (allAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = allAnnotations.get(annotation);
            if (values != null) {
                return Optional.of(new AnnotationValue<>(annotation, values));
            } else if (allStereotypes != null) {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    return Optional.of(new AnnotationValue<>(annotation, values));
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> getDeclaredValues(String annotation) {
        if (declaredAnnotations != null && StringUtils.isNotEmpty(annotation)) {
            Map<CharSequence, Object> values = declaredAnnotations.get(annotation);
            if (values != null) {
                return Optional.of(new AnnotationValue<>(annotation, values));
            } else if (declaredStereotypes != null) {
                values = declaredStereotypes.get(annotation);
                if (values != null) {
                    return Optional.of(new AnnotationValue<>(annotation, values));
                }
            }
        }
        return Optional.empty();
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
            String repeatedName = getRepeatedName(annotation);
            if (repeatedName != null) {
                Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
                if (v instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                    io.micronaut.core.annotation.AnnotationValue[] avs = (io.micronaut.core.annotation.AnnotationValue[]) v;
                    for (io.micronaut.core.annotation.AnnotationValue av : avs) {
                        addRepeatable(annotation, av);
                    }
                } else if (v instanceof Iterable) {
                    Iterable i = (Iterable) v;
                    for (Object o : i) {
                        if (o instanceof io.micronaut.core.annotation.AnnotationValue) {
                            addRepeatable(annotation, ((io.micronaut.core.annotation.AnnotationValue) o));
                        }
                    }
                }
            } else {
                Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
                addAnnotation(annotation, values, null, allAnnotations, false);
            }
        }
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the
     * data will be merged with existing values replaced.
     *
     * @param annotation The annotation
     * @param values     The values
     */
    protected final void addDefaultAnnotationValues(String annotation, Map<CharSequence, Object> values) {
        if (annotation != null) {
            Map<String, Map<CharSequence, Object>> annotationDefaults = this.annotationDefaultValues;
            if (annotationDefaults == null) {
                this.annotationDefaultValues = new HashMap<>();
                annotationDefaults = this.annotationDefaultValues;
            }

            putValues(annotation, values, annotationDefaults);
        }
    }

    /**
     * Returns whether annotation defaults are registered for the give annotation. Used by generated byte code. DO NOT REMOVE.
     *
     * @param annotation The annotation name
     * @return True if defaults have already been registered
     */
    @SuppressWarnings("unused")
    @Internal
    protected static boolean areAnnotationDefaultsRegistered(String annotation) {
        return AnnotationMetadataSupport.hasDefaultValues(annotation);
    }

    /**
     * Registers annotation default values. Used by generated byte code. DO NOT REMOVE.
     *
     * @param annotation The annotation name
     * @param defaultValues The default values
     */
    @SuppressWarnings("unused")
    @Internal
    protected static void registerAnnotationDefaults(String annotation, Map<String, Object> defaultValues) {
        AnnotationMetadataSupport.registerDefaultValues(annotation, defaultValues);
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName The annotation name
     * @param annotationValue The annotation value
     */
    protected final void addRepeatable(String annotationName, io.micronaut.core.annotation.AnnotationValue annotationValue) {
        if (StringUtils.isNotEmpty(annotationName) && annotationValue != null) {
            Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();

            addRepeatableInternal(annotationName, annotationValue, allAnnotations);
        }
    }

    /**
     * Adds a repeatable stereotype value. If a value already exists will be added
     *
     * @param parents The parent annotations
     * @param stereotype The annotation name
     * @param annotationValue The annotation value
     */
    protected void addRepeatableStereotype(List<String> parents, String stereotype, io.micronaut.core.annotation.AnnotationValue annotationValue) {
        Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
        List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
        for (String parentAnnotation : parents) {
            if (!annotationList.contains(parentAnnotation)) {
                annotationList.add(parentAnnotation);
            }
        }

        addRepeatableInternal(stereotype, annotationValue, allStereotypes);
    }

    /**
     * Adds a repeatable declared stereotype value. If a value already exists will be added
     *
     * @param parents The parent annotations
     * @param stereotype The annotation name
     * @param annotationValue The annotation value
     */
    protected void addDeclaredRepeatableStereotype(List<String> parents, String stereotype, io.micronaut.core.annotation.AnnotationValue annotationValue) {
        Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
        List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
        for (String parentAnnotation : parents) {
            if (!annotationList.contains(parentAnnotation)) {
                annotationList.add(parentAnnotation);
            }
        }

        addRepeatableInternal(stereotype, annotationValue, declaredStereotypes);
        addRepeatableInternal(stereotype, annotationValue, getAllStereotypes());
    }

    /**
     * Adds a repeatable annotation value. If a value already exists will be added
     *
     * @param annotationName The annotation name
     * @param annotationValue The annotation value
     */
    protected final void addDeclaredRepeatable(String annotationName, io.micronaut.core.annotation.AnnotationValue annotationValue) {
        if (StringUtils.isNotEmpty(annotationName) && annotationValue != null) {
            Map<String, Map<CharSequence, Object>> allAnnotations = getDeclaredAnnotationsInternal();

            addRepeatableInternal(annotationName, annotationValue, allAnnotations);

            addRepeatable(annotationName, annotationValue);
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
            String repeatedName = getRepeatedName(stereotype);
            if (repeatedName != null) {
                Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
                if (v instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                    io.micronaut.core.annotation.AnnotationValue[] avs = (io.micronaut.core.annotation.AnnotationValue[]) v;
                    for (io.micronaut.core.annotation.AnnotationValue av : avs) {
                        addRepeatableStereotype(parentAnnotations, stereotype, av);
                    }
                } else if (v instanceof Iterable) {
                    Iterable i = (Iterable) v;
                    for (Object o : i) {
                        if (o instanceof io.micronaut.core.annotation.AnnotationValue) {
                            addRepeatableStereotype(parentAnnotations, stereotype, (io.micronaut.core.annotation.AnnotationValue) o);
                        }
                    }
                }
            } else {
                Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
                List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
                for (String parentAnnotation : parentAnnotations) {
                    if (!annotationList.contains(parentAnnotation)) {
                        annotationList.add(parentAnnotation);
                    }
                }

                // add to stereotypes
                addAnnotation(
                        stereotype,
                        values,
                        null,
                        allStereotypes,
                        false
                );
            }

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
            String repeatedName = getRepeatedName(stereotype);
            if (repeatedName != null) {
                Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
                if (v instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                    io.micronaut.core.annotation.AnnotationValue[] avs = (io.micronaut.core.annotation.AnnotationValue[]) v;
                    for (io.micronaut.core.annotation.AnnotationValue av : avs) {
                        addDeclaredRepeatableStereotype(parentAnnotations, stereotype, av);
                    }
                } else if (v instanceof Iterable) {
                    Iterable i = (Iterable) v;
                    for (Object o : i) {
                        if (o instanceof io.micronaut.core.annotation.AnnotationValue) {
                            addDeclaredRepeatableStereotype(parentAnnotations, stereotype, (io.micronaut.core.annotation.AnnotationValue) o);
                        }
                    }
                }
            } else {
                Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
                Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
                List<String> annotationList = getAnnotationsByStereotypeInternal(stereotype);
                for (String parentAnnotation : parentAnnotations) {
                    if (!annotationList.contains(parentAnnotation)) {
                        annotationList.add(parentAnnotation);
                    }
                }

                addAnnotation(
                        stereotype,
                        values,
                        declaredStereotypes,
                        allStereotypes,
                        true
                );
            }

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
            String repeatedName = getRepeatedName(annotation);
            if (repeatedName != null) {
                Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
                if (v instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                    io.micronaut.core.annotation.AnnotationValue[] avs = (io.micronaut.core.annotation.AnnotationValue[]) v;
                    for (io.micronaut.core.annotation.AnnotationValue av : avs) {
                        addDeclaredRepeatable(annotation, av);
                    }
                } else if (v instanceof Iterable) {
                    Iterable i = (Iterable) v;
                    for (Object o : i) {
                        if (o instanceof io.micronaut.core.annotation.AnnotationValue) {
                            addDeclaredRepeatable(annotation, ((io.micronaut.core.annotation.AnnotationValue) o));
                        }
                    }
                }
            } else {
                Map<String, Map<CharSequence, Object>> declaredAnnotations = getDeclaredAnnotationsInternal();
                Map<String, Map<CharSequence, Object>> allAnnotations = getAllAnnotations();
                addAnnotation(annotation, values, declaredAnnotations, allAnnotations, true);
            }
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

    private <T extends Annotation> List<io.micronaut.core.annotation.AnnotationValue<T>> resolveAnnotationValuesByType(Class<T> annotationType, Map<String, Map<CharSequence, Object>> sourceAnnotations, Map<String, Map<CharSequence, Object>> sourceStereotypes) {
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            Class<? extends Annotation> repeatableType = repeatable.value();
            if (hasStereotype(repeatableType)) {
                List<io.micronaut.core.annotation.AnnotationValue<T>> results = new ArrayList<>();
                if (sourceAnnotations != null) {
                    Map<CharSequence, Object> values = sourceAnnotations.get(repeatableType.getName());
                    addAnnotationValuesFromData(results, values);
                }

                if (sourceStereotypes != null) {
                    Map<CharSequence, Object> values = sourceStereotypes.get(repeatableType.getName());
                    addAnnotationValuesFromData(results, values);
                }

                return results;
            }
        }
        return null;
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

    private String getRepeatedName(String annotation) {
        if (repeated != null) {
            return repeated.get(annotation);
        }
        return null;
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

    private void addRepeatableInternal(String annotationName, io.micronaut.core.annotation.AnnotationValue annotationValue, Map<String, Map<CharSequence, Object>> allAnnotations) {
        addRepeatableInternal(annotationName, AnnotationMetadata.VALUE_MEMBER, annotationValue, allAnnotations);
    }

    private void addRepeatableInternal(String annotationName, String member, io.micronaut.core.annotation.AnnotationValue annotationValue, Map<String, Map<CharSequence, Object>> allAnnotations) {
        if (repeated == null) {
            repeated = new HashMap<>(2);
        }

        repeated.put(annotationName, annotationValue.getAnnotationName());

        Map<CharSequence, Object> values = allAnnotations.computeIfAbsent(annotationName, s -> new HashMap<>());
        Object v = values.get(member);
        if (v != null) {
            if (v.getClass().isArray()) {
                Object[] array = (Object[]) v;
                List newValues = new ArrayList(array.length + 1);
                newValues.addAll(Arrays.asList(array));
                newValues.add(annotationValue);
                values.put(member, newValues);
            } else if (v instanceof Collection) {
                ((Collection) v).add(annotationValue);
            }
        } else {
            ArrayList<Object> newValues = new ArrayList<>(2);
            newValues.add(annotationValue);
            values.put(member, newValues);
        }
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
