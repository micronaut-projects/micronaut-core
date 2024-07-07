/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Abstract annotation metadata delegate for cases when annotation
 * values need to be mapped before being returned.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Experimental
public abstract sealed class MappingAnnotationMetadataDelegate implements AnnotationMetadataDelegate permits EvaluatedAnnotationMetadata {
    public abstract <T extends Annotation> AnnotationValue<T> mapAnnotationValue(AnnotationValue<T> av);


    @Override
    public Optional<String> stringValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.stringValue(member));
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation, String member) {
        return stringValue(annotation.getName(), member);
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation) {
        return stringValue(annotation, VALUE_MEMBER);
    }

    @Override
    public Optional<String> stringValue(String annotation) {
        return stringValue(annotation, VALUE_MEMBER);
    }

    @Override
    public String[] stringValues(String annotation, String member) {
        return findAnnotation(annotation)
                   .map(av -> av.stringValues(member))
                   .orElse(StringUtils.EMPTY_STRING_ARRAY);
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation, String member) {
        return stringValues(annotation.getName(), member);
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation) {
        return stringValues(annotation, VALUE_MEMBER);
    }

    @Override
    public String[] stringValues(String annotation) {
        return stringValues(annotation, VALUE_MEMBER);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, Class<E> enumType) {
        return enumValue(annotation, VALUE_MEMBER, enumType);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, String member,
                                                     Class<E> enumType) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.enumValue(member, enumType));
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation,
                                                     Class<E> enumType) {
        return enumValue(annotation.getName(), VALUE_MEMBER, enumType);
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation,
                                                     String member, Class<E> enumType) {
        return enumValue(annotation.getName(), member, enumType);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType) {
        return findAnnotation(annotation)
                   .map(av -> av.enumValues(member, enumType))
                   .orElse((E[]) Array.newInstance(enumType, 0));
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, Class<E> enumType) {
        return enumValues(annotation, VALUE_MEMBER, enumType);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation,
                                              Class<E> enumType) {
        return enumValues(annotation.getName(), VALUE_MEMBER, enumType);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation,
                                              String member, Class<E> enumType) {
        return enumValues(annotation.getName(), member, enumType);
    }

    @Override
    public <T> Class<T>[] classValues(String annotation, String member) {
        return (Class<T>[]) findAnnotation(annotation)
                                .map(av -> av.classValues(member))
                                .orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    @Override
    public <T> Class<T>[] classValues(String annotation) {
        return classValues(annotation, VALUE_MEMBER);
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation) {
        return classValues(annotation.getName(), VALUE_MEMBER);
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation, String member) {
        return classValues(annotation.getName(), member);
    }

    @Override
    public Optional<Boolean> booleanValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.booleanValue(member));
    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member) {
        return booleanValue(annotation.getName(), member);
    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation) {
        return booleanValue(annotation.getName(), VALUE_MEMBER);
    }

    @Override
    public Optional<Boolean> booleanValue(String annotation) {
        return booleanValue(annotation, VALUE_MEMBER);
    }

    @Override
    public boolean isTrue(String annotation, String member) {
        return getValue(annotation, member, Boolean.class).orElse(false);
    }

    @Override
    public boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return isTrue(annotation.getName(), member);
    }

    @Override
    public boolean isFalse(String annotation, String member) {
        return !isTrue(annotation, member);
    }

    @Override
    public boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return isFalse(annotation.getName(), member);
    }

    @Override
    public Optional<Class> classValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.classValue(member));
    }

    @Override
    public Optional<Class> classValue(String annotation) {
        return classValue(annotation, VALUE_MEMBER);
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation) {
        return classValue(annotation.getName(), VALUE_MEMBER);
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return classValue(annotation.getName(), member);
    }

    @Override
    public OptionalInt intValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .map(AnnotationValue::intValue)
                   .orElse(OptionalInt.empty());
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation, String member) {
        return intValue(annotation.getName(), member);
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation) {
        return intValue(annotation.getName(), VALUE_MEMBER);
    }

    @Override
    public OptionalLong longValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .map(AnnotationValue::longValue)
                   .orElse(OptionalLong.empty());
    }

    @Override
    public OptionalLong longValue(Class<? extends Annotation> annotation, String member) {
        return longValue(annotation.getName(), member);
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member) {
        return findAnnotation(annotation)
                   .map(av -> av.doubleValue(member))
                   .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member) {
        return findAnnotation(annotation)
                   .map(av -> av.doubleValue(member))
                   .orElse(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation) {
        return doubleValue(annotation, VALUE_MEMBER);
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.get(member, requiredType));
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member,
                                    Argument<T> requiredType) {
        return findAnnotation(annotation)
                   .flatMap(av -> av.get(member, requiredType));
    }

    @Override
    public <T> Optional<T> getValue(String annotation, Argument<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation,
                                    Argument<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member,
                                    Class<T> requiredType) {
        return getValue(annotation, member, Argument.of(requiredType));
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        return getValue(annotation, member, Argument.of(requiredType));
    }

    @Override
    public <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, Argument.of(requiredType));
    }

    @Override
    public Optional<Object> getValue(String annotation, String member) {
        return getValue(annotation, member, Object.class);
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        return getValue(annotation, member, Object.class);
    }

    @Override
    public Optional<Object> getValue(String annotation) {
        return getValue(annotation, VALUE_MEMBER, Object.class);
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation) {
        return getValue(annotation, VALUE_MEMBER, Object.class);
    }

    @Override
    public <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation,
                                           Class<T> valueType) {
        return getValues(annotation.getName(), valueType);
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        return OptionalValues.of(valueType, getValues(annotation));
    }

    @Override
    public Map<CharSequence, Object> getValues(String annotation) {
        return findAnnotation(annotation)
                   .map(AnnotationValue::getValues)
                   .orElse(Collections.emptyMap());
    }

    @Override
    public <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(Class<T> annotationClass) {
        AnnotationValue<T> av = getAnnotationMetadata().getDeclaredAnnotation(annotationClass);
        if (av != null) {
            return mapAnnotationValue(av);
        }
        return null;
    }

    @Override
    public <T extends Annotation> AnnotationValue<T> getAnnotation(Class<T> annotationClass) {
        return getAnnotation(annotationClass.getName());
    }

    @Override
    public <T extends Annotation> AnnotationValue<T> getAnnotation(String annotation) {
        AnnotationValue<T> av = getAnnotationMetadata().getAnnotation(annotation);
        if (av != null) {
            return mapAnnotationValue(av);
        }
        return null;
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        AnnotationValue<Annotation> av = getAnnotationMetadata().getAnnotation(annotation);
        if (av != null) {
            //noinspection unchecked
            return Optional.of((AnnotationValue<T>) mapAnnotationValue(av));
        }
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().findAnnotation(annotationClass)
                   .map(this::mapAnnotationValue);
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        return findDeclaredAnnotation(annotationClass.getName());
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        Optional<AnnotationValue<T>> av =
            getAnnotationMetadata().findDeclaredAnnotation(annotation);
        return av.map(this::mapAnnotationValue);
    }

    @Override
    public <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getDeclaredAnnotationValuesByType(annotationClass).stream()
                   .map(annotation -> AnnotationMetadataSupport.buildAnnotation(annotationClass,
                       annotation))
                   .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @Override
    public <T extends Annotation> T[] synthesizeAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getAnnotationValuesByType(annotationClass).stream()
                   .map(annotation -> AnnotationMetadataSupport.buildAnnotation(annotationClass,
                       annotation))
                   .toArray(value -> (T[]) Array.newInstance(annotationClass, value));
    }

    @Override
    public <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        return findDeclaredAnnotation(annotationClass)
                   .map(av -> AnnotationMetadataSupport.buildAnnotation(annotationClass, av))
                   .orElse(null);
    }

    @Override
    public <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        return findAnnotation(annotationClass)
                   .map(av -> AnnotationMetadataSupport.buildAnnotation(annotationClass, av))
                   .orElse(null);
    }

    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass, String sourceAnnotation) {
        AnnotationValue<T> av = getAnnotation(sourceAnnotation);
        if (av != null) {
            return AnnotationMetadataSupport.buildAnnotation(annotationClass, av);
        }
        return null;
    }

    @Override
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass, String sourceAnnotation) {
        AnnotationValue<T> av = getDeclaredAnnotation(sourceAnnotation);
        if (av != null) {
            return AnnotationMetadataSupport.buildAnnotation(annotationClass, av);
        }
        return null;
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
        return getAnnotationValues(() -> getAnnotationMetadata().getAnnotationValuesByType(annotationType));
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
        return getAnnotationValues(() -> getAnnotationMetadata().getDeclaredAnnotationValuesByType(annotationType));
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(String stereotype) {
        return getAnnotationValues(() -> getAnnotationMetadata().getAnnotationValuesByStereotype(stereotype));
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(String annotationType) {
        return getAnnotationValues(() -> getAnnotationMetadata().getDeclaredAnnotationValuesByName(annotationType));
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        return getAnnotationValues(() -> getAnnotationMetadata().getAnnotationValuesByName(annotationType));
    }

    private <T extends Annotation> List<AnnotationValue<T>> getAnnotationValues(Supplier<List<AnnotationValue<T>>> supplier) {
        return supplier.get().stream()
                   .map(this::mapAnnotationValue)
                   .toList();
    }
}
