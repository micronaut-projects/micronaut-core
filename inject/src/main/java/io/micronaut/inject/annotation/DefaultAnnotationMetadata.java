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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link AnnotationMetadata}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultAnnotationMetadata implements AnnotationMetadata, AnnotatedElement {

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
                    if (!aClass.isPresent()) break;
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
    Map<String, Map<CharSequence, Object>> declaredStereotypes;
    Map<String, Map<CharSequence, Object>> allStereotypes;
    Map<String, Map<CharSequence, Object>> allAnnotations;
    Map<String, Set<String>> annotationsByStereotype;

    private Annotation[] allAnnotationArray;
    private Annotation[] declaredAnnotationArray;
    private final Map<String, Annotation> annotationMap;
    private final Map<String, Annotation> declaredAnnotationMap;
    private Environment environment;

    /**
     * Constructs empty annotation metadata
     */
    @Internal
    protected DefaultAnnotationMetadata() {
        annotationMap = new ConcurrentHashMap<>(2);
        declaredAnnotationMap = new ConcurrentHashMap<>(2);
    }

    /**
     * This constructor is designed to be used by compile time produced subclasses
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
        @Nullable Map<String, Set<String>> annotationsByStereotype) {
        this.declaredAnnotations = declaredAnnotations;
        this.declaredStereotypes = declaredStereotypes;
        this.allStereotypes = allStereotypes;
        this.allAnnotations = allAnnotations;
        this.declaredAnnotationMap = declaredAnnotations != null ? new ConcurrentHashMap<>(declaredAnnotations.size()) : null;
        this.annotationMap = allAnnotations != null ? new ConcurrentHashMap<>(allAnnotations.size()) : null;
        this.annotationsByStereotype = annotationsByStereotype;
    }

    /**
     * Configures annotation metadata for the environment. This is an internal method and should not be called directly
     *
     * @param context The context
     */
    @Internal
    public void configure(BeanContext context) {
        if (context instanceof ApplicationContext) {
            ApplicationContext applicationContext = (ApplicationContext) context;
            this.environment = applicationContext.getEnvironment();
        }
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
    public Set<String> getAnnotationNamesByStereotype(String stereotype) {
        if (annotationsByStereotype != null) {
            Set<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                return Collections.unmodifiableSet(annotations);
            }
        }
        if (allAnnotations != null && allAnnotations.containsKey(stereotype)) {
            return CollectionUtils.setOf(stereotype);
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return CollectionUtils.setOf(stereotype);
        }
        return Collections.emptySet();
    }

    @Override
    public Set<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        if (annotationsByStereotype != null) {
            Set<String> annotations = annotationsByStereotype.get(stereotype);
            if (annotations != null) {
                annotations = new HashSet<>(annotations);
                if (declaredAnnotations != null) {
                    annotations.removeIf(s -> !declaredAnnotations.containsKey(s));
                    return Collections.unmodifiableSet(annotations);
                } else {
                    // no declared
                    return Collections.emptySet();
                }
            }
        }
        if (declaredAnnotations != null && declaredAnnotations.containsKey(stereotype)) {
            return CollectionUtils.setOf(stereotype);
        }
        return Collections.emptySet();
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
                if (environment != null) {
                    return new EnvironmentOptionalValuesMap<>(valueType, values, environment);
                } else {
                    return OptionalValues.of(valueType, values);
                }
            } else {
                values = allStereotypes.get(annotation);
                if (values != null) {
                    if (environment != null) {
                        return new EnvironmentOptionalValuesMap<>(valueType, values, environment);
                    } else {
                        return OptionalValues.of(valueType, values);
                    }
                }
            }
        }
        return OptionalValues.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass == null || annotationMap == null) return null;
        String annotationName = annotationClass.getName().intern();
        if (hasAnnotation(annotationName) || hasStereotype(annotationName)) {
            return (T) annotationMap.computeIfAbsent(annotationName, s -> {
                ConvertibleValues<Object> annotationValues = getValues(annotationClass);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValues);

            });
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        if (annotationClass == null || declaredAnnotationMap == null) return null;
        String annotationName = annotationClass.getName().intern();
        if (hasAnnotation(annotationName) || hasStereotype(annotationName)) {
            return (T) declaredAnnotationMap.computeIfAbsent(annotationName, s -> {
                ConvertibleValues<Object> annotationValues = getValues(annotationClass);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValues);

            });
        }
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        if (annotationMap == null) return AnnotationUtil.ZERO_ANNOTATIONS;
        Annotation[] annotations = this.allAnnotationArray;
        if (annotations == null) {
            synchronized (this) { // double check
                annotations = this.allAnnotationArray;
                if (annotations == null) {
                    this.allAnnotationArray = annotations = initializeAnnotations(allAnnotations.keySet());
                }
            }
        }
        return annotations;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        if (declaredAnnotationMap == null) return AnnotationUtil.ZERO_ANNOTATIONS;
        Annotation[] annotations = this.declaredAnnotationArray;
        if (annotations == null) {
            synchronized (this) { // double check
                annotations = this.declaredAnnotationArray;
                if (annotations == null) {
                    this.declaredAnnotationArray = annotations = initializeAnnotations(declaredAnnotations.keySet());
                }
            }
        }
        return annotations;
    }

    /**
     * Adds an annotation and its member values, if the annotation already exists the data will be merged with existing values replaced
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
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing values replaced
     *
     * @param stereotype The annotation
     * @param values     The values
     */
    @SuppressWarnings("WeakerAccess")
    protected final void addStereotype(Set<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        if (stereotype != null) {
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            getAnnotationsByStereotypeInternal(stereotype).addAll(parentAnnotations);
            addAnnotation(stereotype, values, null, allStereotypes, false);
        }
    }

    /**
     * Adds a stereotype and its member values, if the annotation already exists the data will be merged with existing values replaced
     *
     * @param stereotype The annotation
     * @param values     The values
     */
    @SuppressWarnings("WeakerAccess")
    protected final void addDeclaredStereotype(Set<String> parentAnnotations, String stereotype, Map<CharSequence, Object> values) {
        if (stereotype != null) {
            Map<String, Map<CharSequence, Object>> declaredStereotypes = getDeclaredStereotypesInternal();
            Map<String, Map<CharSequence, Object>> allStereotypes = getAllStereotypes();
            getAnnotationsByStereotypeInternal(stereotype).addAll(parentAnnotations);
            addAnnotation(stereotype, values, declaredStereotypes, allStereotypes, true);
        }
    }

    /**
     * Adds an annotation directly declared on the element and its member values, if the annotation already exists the data will be merged with existing values replaced
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
        if (environment != null) {
            return EnvironmentConvertibleValuesMap.of(environment, values);
        } else {
            return ConvertibleValues.of(values);
        }
    }

    private Map<String, Map<CharSequence, Object>> getAllStereotypes() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.allStereotypes;
        if (stereotypes == null) {
            this.allStereotypes = stereotypes = new HashMap<>(3);
        }
        return stereotypes;
    }

    private Map<String, Map<CharSequence, Object>> getDeclaredStereotypesInternal() {
        Map<String, Map<CharSequence, Object>> stereotypes = this.declaredStereotypes;
        if (stereotypes == null) {
            this.declaredStereotypes = stereotypes = new HashMap<>(3);
        }
        return stereotypes;
    }

    private Map<String, Map<CharSequence, Object>> getAllAnnotations() {
        Map<String, Map<CharSequence, Object>> annotations = this.allAnnotations;
        if (annotations == null) {
            this.allAnnotations = annotations = new HashMap<>(3);
        }
        return annotations;
    }

    private Map<String, Map<CharSequence, Object>> getDeclaredAnnotationsInternal() {
        Map<String, Map<CharSequence, Object>> annotations = this.declaredAnnotations;
        if (annotations == null) {
            this.declaredAnnotations = annotations = new HashMap<>(3);
        }
        return annotations;
    }

    private Set<String> getAnnotationsByStereotypeInternal(String stereotype) {
        return getAnnotationsByStereotypeInternal().computeIfAbsent(stereotype, s -> new HashSet<>());
    }

    private Map<String, Set<String>> getAnnotationsByStereotypeInternal() {
        Map<String, Set<String>> annotations = this.annotationsByStereotype;
        if (annotations == null) {
            this.annotationsByStereotype = annotations = new HashMap<>(3);
        }
        return annotations;
    }

    private Annotation[] initializeAnnotations(Set<String> names) {
        if (CollectionUtils.isNotEmpty(names)) {
            List<Annotation> annotations = new ArrayList<>();
            for (String name : names) {
                Optional<Class> loaded = ClassUtils.forName(name, getClass().getClassLoader());
                loaded.ifPresent(aClass -> {
                    Annotation ann = getAnnotation(aClass);
                    if (ann != null)
                        annotations.add(ann);
                });
            }
            return annotations.toArray(new Annotation[annotations.size()]);
        }

        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    /**
     * Creates an {@link AnnotationMetadata} for the given property containing a {@link Property} annotation with the name member set to the value of the property.
     *
     * @param property The property
     * @return The metadata
     */
    public static AnnotationMetadata addProperty(AnnotationMetadata annotationMetadata, String property) {
        if(StringUtils.isEmpty(property)) {
            throw new IllegalArgumentException("Property argument cannot be blank");
        }
        if(!(annotationMetadata instanceof DefaultAnnotationMetadata)) {
            return forProperty(property);
        }
        else {
            ((DefaultAnnotationMetadata)annotationMetadata)
                    .addDeclaredAnnotation(Property.class.getName(), Collections.singletonMap(
                    "name", property
            ));

            return annotationMetadata;
        }
    }

    private static AnnotationMetadata forProperty(String property) {
        if(StringUtils.isEmpty(property)) {
            throw new IllegalArgumentException("Property argument cannot be blank");
        }
        return new DefaultAnnotationMetadata() {{
            addDeclaredAnnotation(Property.class.getName(), Collections.singletonMap(
                    "name", property
            ));
        }};
    }
}
