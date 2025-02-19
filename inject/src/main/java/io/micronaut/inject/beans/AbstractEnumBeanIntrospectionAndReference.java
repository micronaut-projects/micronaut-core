/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.beans;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.type.Argument;

import java.util.List;

/**
 * A variation of {@link AbstractInitializableBeanIntrospection} that is also a {@link BeanIntrospectionReference}.
 *
 * @param <E> The enum type
 * @author Denis Stepanov
 * @since 4.4.0
 */
@Internal
public abstract class AbstractEnumBeanIntrospectionAndReference<E extends Enum<E>> extends AbstractInitializableBeanIntrospectionAndReference<E> implements EnumBeanIntrospection<E> {

    private final List<EnumConstant<E>> enumConstantRefs;

    /**
     * The default constructor.
     *
     * @param beanType                      The bean type
     * @param annotationMetadata            The annotation metadata
     * @param constructorAnnotationMetadata The constructor annotation metadata
     * @param constructorArguments          The constructor arguments
     * @param propertiesRefs                The property references
     * @param methodsRefs                   The method references
     * @param enumValueRefs                 The enum references
     * @deprecated the type {@link EnumConstantRef} is replaced by {@link EnumConstantDynamicRef}
     */
    @Deprecated
    protected AbstractEnumBeanIntrospectionAndReference(Class<E> beanType,
                                                        AnnotationMetadata annotationMetadata,
                                                        AnnotationMetadata constructorAnnotationMetadata,
                                                        Argument<?>[] constructorArguments,
                                                        BeanPropertyRef<Object>[] propertiesRefs,
                                                        BeanMethodRef<Object>[] methodsRefs,
                                                        EnumConstantRef<E>[] enumValueRefs) {
        super(beanType, annotationMetadata, constructorAnnotationMetadata, constructorArguments, propertiesRefs, methodsRefs);
        this.enumConstantRefs = List.of(enumValueRefs);
    }

    /**
     * The default constructor.
     *
     * @param beanType                      The bean type
     * @param annotationMetadata            The annotation metadata
     * @param constructorAnnotationMetadata The constructor annotation metadata
     * @param constructorArguments          The constructor arguments
     * @param propertiesRefs                The property references
     * @param methodsRefs                   The method references
     * @param enumValueRefs                 The enum references
     */
    protected AbstractEnumBeanIntrospectionAndReference(Class<E> beanType,
                                                        AnnotationMetadata annotationMetadata,
                                                        AnnotationMetadata constructorAnnotationMetadata,
                                                        Argument<?>[] constructorArguments,
                                                        BeanPropertyRef<Object>[] propertiesRefs,
                                                        BeanMethodRef<Object>[] methodsRefs,
                                                        EnumConstantDynamicRef<E>[] enumValueRefs) {
        super(beanType, annotationMetadata, constructorAnnotationMetadata, constructorArguments, propertiesRefs, methodsRefs);
        this.enumConstantRefs = List.of(enumValueRefs);
    }

    @NonNull
    @Override
    public List<EnumConstant<E>> getConstants() {
        return enumConstantRefs;
    }

    /**
     * Enum value compile-time data container.
     * @deprecated the type {@link EnumConstantRef} is replaced by {@link EnumConstantDynamicRef}
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    public record EnumConstantRef<E extends Enum<E>>(@NonNull E value,
                                                     @NonNull AnnotationMetadata annotationMetadata) implements EnumConstant<E> {

        @NonNull
        @Override
        public E getValue() {
            return value;
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }
    }

    /**
     * Enum value compile-time data container.
     */
    @Internal
    @UsedByGeneratedCode
    public record EnumConstantDynamicRef<E extends Enum<E>>(@NonNull AnnotationClassValue<E> enumClass,
                                                            @NonNull String name,
                                                            @NonNull AnnotationMetadata annotationMetadata) implements EnumConstant<E> {

        @NonNull
        @Override
        public E getValue() {
            Class<E> type = enumClass.getType().orElse(null);
            if (type == null) {
                throw new IllegalStateException("Enum type: " + enumClass.getType() + " is not present on the classpath!");
            }
            return Enum.valueOf(type, name);
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }
    }
}
