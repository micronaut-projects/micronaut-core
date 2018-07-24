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

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Variation of {@link AnnotationMetadata} that is environment specific.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractEnvironmentAnnotationMetadata extends AbstractAnnotationMetadata implements AnnotationMetadata {

    private final DefaultAnnotationMetadata annotationMetadata;

    /**
     * Construct a new environment aware annotation metadata.
     *
     * @param targetMetadata The target annotation metadata
     */
    protected AbstractEnvironmentAnnotationMetadata(DefaultAnnotationMetadata targetMetadata) {
        super(targetMetadata.declaredAnnotations, targetMetadata.allAnnotations);
        this.annotationMetadata = targetMetadata;
    }

    @Override
    public List<AnnotationValue> getAnnotationValuesByType(Class<? extends Annotation> annotationType) {
        Environment environment = getEnvironment();
        List<AnnotationValue> values = annotationMetadata.getAnnotationValuesByType(annotationType);
        if (environment != null) {
            return values.stream().map(entries ->
                    new EnvironmentAnnotationValue(environment, entries)
            ).collect(Collectors.toList());
        }
        return values;
    }

    @Override
    public List<AnnotationValue> getDeclaredAnnotationValuesByType(Class<? extends Annotation> annotationType) {
        Environment environment = getEnvironment();
        List<AnnotationValue> values = annotationMetadata.getDeclaredAnnotationValuesByType(annotationType);
        if (environment != null) {
            return values.stream().map(entries -> new EnvironmentAnnotationValue(environment, entries))
                    .collect(Collectors.toList());
        }
        return values;

    }

    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        Environment environment = getEnvironment();
        if (environment != null) {

            List<AnnotationValue> values = annotationMetadata.getAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, EnvironmentConvertibleValuesMap.of(environment, entries.getValues())))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return annotationMetadata.getAnnotationsByType(annotationClass);
        }
    }

    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        Environment environment = getEnvironment();
        if (environment != null) {

            List<AnnotationValue> values = annotationMetadata.getDeclaredAnnotationValuesByType(annotationClass);

            return values.stream()
                    .map(entries -> AnnotationMetadataSupport.buildAnnotation(annotationClass, EnvironmentConvertibleValuesMap.of(environment, entries.getValues())))
                    .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
        } else {
            return annotationMetadata.getDeclaredAnnotationsByType(annotationClass);
        }
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        return annotationMetadata.hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return annotationMetadata.hasStereotype(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return annotationMetadata.hasDeclaredStereotype(annotation);
    }

    @Override
    public List<String> getAnnotationNamesByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public Set<String> getAnnotationNames() {
        return annotationMetadata.getAnnotationNames();
    }

    @Override
    public Set<String> getDeclaredAnnotationNames() {
        return annotationMetadata.getDeclaredAnnotationNames();
    }

    @Override
    public List<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        return annotationMetadata.getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public ConvertibleValues<Object> getValues(String annotation) {
        Map<String, Map<CharSequence, Object>> allAnnotations = annotationMetadata.allAnnotations;
        Map<String, Map<CharSequence, Object>> allStereotypes = annotationMetadata.allStereotypes;
        return resolveValuesForEnvironment(annotation, allAnnotations, allStereotypes);
    }

    @Override
    public ConvertibleValues<Object> getDeclaredValues(String annotation) {
        Map<String, Map<CharSequence, Object>> allAnnotations = annotationMetadata.declaredAnnotations;
        Map<String, Map<CharSequence, Object>> allStereotypes = annotationMetadata.declaredStereotypes;
        return resolveValuesForEnvironment(annotation, allAnnotations, allStereotypes);
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        Map<String, Map<CharSequence, Object>> allAnnotations = annotationMetadata.allAnnotations;
        Map<String, Map<CharSequence, Object>> allStereotypes = annotationMetadata.allStereotypes;
        Environment environment = getEnvironment();
        OptionalValues<T> values = resolveOptionalValuesForEnvironment(annotation, valueType, allAnnotations, allStereotypes, environment);
        if (values != null) {
            return values;
        }
        return OptionalValues.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return annotationMetadata.getDefaultValue(annotation, member, requiredType);
    }


    /**
     * Resolves the {@link Environment} for this metadata.
     *
     * @return The metadata
     */
    protected abstract @Nullable Environment getEnvironment();

    @Override
    protected void addValuesToResults(List<AnnotationValue> results, AnnotationValue values) {
        Environment environment = getEnvironment();
        if (environment != null) {
            results.add(new EnvironmentAnnotationValue(environment, values));
        } else {
            results.add(values);
        }
    }

    private ConvertibleValues<Object> resolveValuesForEnvironment(String annotation, Map<String, Map<CharSequence, Object>> allAnnotations, Map<String, Map<CharSequence, Object>> allStereotypes) {
        if (StringUtils.isNotEmpty(annotation)) {
            if (allAnnotations != null) {
                Map<CharSequence, Object> values = allAnnotations.get(annotation);
                if (values != null) {
                    return convertibleValuesOf(values);
                } else if (allStereotypes != null) {
                    return convertibleValuesOf(allStereotypes.get(annotation));
                }
            }
        }
        return ConvertibleValuesMap.empty();
    }

    private ConvertibleValues<Object> convertibleValuesOf(Map<CharSequence, Object> values) {
        Environment environment = getEnvironment();
        if (environment != null) {
            return EnvironmentConvertibleValuesMap.of(environment, values);
        } else {
            return ConvertibleValues.of(values);
        }
    }

    private <T> OptionalValues<T> resolveOptionalValuesForEnvironment(String annotation, Class<T> valueType, Map<String, Map<CharSequence, Object>> allAnnotations, Map<String, Map<CharSequence, Object>> allStereotypes, Environment environment) {
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
        return null;
    }
}
