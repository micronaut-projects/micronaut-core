/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.beans.BeanElementBuilder;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Stores data about an element that references a method.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface MethodElement extends MemberElement {

    /**
     * Returns the method annotations.
     * The method will only return annotations defined on a method or inherited from the super methods,
     * while {@link #getAnnotationMetadata()} for a method combines the class and the method annotations.
     * NOTE: For a constructor {@link #getAnnotationMetadata()} will not combine the class annotations.
     *
     * @return The method annotation metadata
     * @since 4.0.0
     */
    @NonNull
    default MutableAnnotationMetadataDelegate<AnnotationMetadata> getMethodAnnotationMetadata() {
        return new MutableAnnotationMetadataDelegate<>() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return MethodElement.this.getAnnotationMetadata();
            }
        };
    }

    /**
     * @return The return type of the method
     */
    @NonNull
    ClassElement getReturnType();

    /**
     * @return The type arguments declared on this method.
     */
    default List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        return Collections.emptyList();
    }

    /**
     * The type arguments for this method element.
     * The type arguments should include the type arguments added to the method plus the type arguments of the declaring class.
     *
     * @return The type arguments for this method element
     * @since 4.0.0
     */
    @Experimental
    @NonNull
    default Map<String, ClassElement> getTypeArguments() {
        Map<String, ClassElement> typeArguments = getDeclaringType().getTypeArguments();
        Map<String, ClassElement> methodTypeArguments = getDeclaredTypeArguments();
        Map<String, ClassElement> newTypeArguments = CollectionUtils.newLinkedHashMap(typeArguments.size() + methodTypeArguments.size());
        newTypeArguments.putAll(typeArguments);
        newTypeArguments.putAll(methodTypeArguments);
        return newTypeArguments;
    }

    /**
     * The declared type arguments for this method element.
     *
     * @return The declared type arguments for this method element
     * @since 4.0.0
     */
    @Experimental
    @NonNull
    default Map<String, ClassElement> getDeclaredTypeArguments() {
        return Collections.emptyMap();
    }

    /**
     * <p>Returns the receiver type of this executable, or empty if the method has no receiver type.</p>
     *
     * <p>A MethodElement which is an instance method, or a constructor of an inner class, has a receiver type derived from the declaring type.</p>
     *
     * <p>A MethodElement which is a static method, or a constructor of a non-inner class, or an initializer (static or instance), has no receiver type.</p>
     *
     * @return The receiver type for the method if one exists.
     * @since 3.1.0
     */
    default Optional<ClassElement> getReceiverType() {
        return Optional.empty();
    }

    /**
     * Returns the types declared in the {@code throws} declaration of a method.
     *
     * @return The {@code throws} types, if any. Never {@code null}.
     * @since 3.1.0
     */
    @NonNull
    default ClassElement[] getThrownTypes() {
        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    /**
     * @return The method parameters
     */
    @NonNull
    ParameterElement[] getParameters();

    /**
     * Takes this method element and transforms into a new method element with the given parameters appended to the existing parameters.
     *
     * @param newParameters The new parameters
     * @return A new method element
     * @since 2.3.0
     */
    @NonNull
    default MethodElement withNewParameters(@NonNull ParameterElement... newParameters) {
        return withParameters(ArrayUtils.concat(getParameters(), newParameters));
    }

    /**
     * Takes this method element and transforms into a new method element with the given parameters.
     *
     * @param newParameters The new parameters
     * @return A new method element
     * @since 4.0.0
     */
    @NonNull
    MethodElement withParameters(@NonNull ParameterElement... newParameters);

    /**
     * Returns a new method with a new owning type.
     *
     * @param owningType The owning type.
     * @return A new method element
     * @since 4.0.0
     */
    @NonNull
    default MethodElement withNewOwningType(@NonNull ClassElement owningType) {
        throw new IllegalStateException("Not supported to change the owning type!");
    }

    /**
     * This method adds an associated bean using this method element as the originating element.
     *
     * <p>Note that this method can only be called on classes being directly compiled by Micronaut. If the ClassElement is
     * loaded from pre-compiled code an {@link UnsupportedOperationException} will be thrown.</p>
     *
     * @param type The type of the bean
     * @return A bean builder
     */
    default @NonNull
    BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
        throw new UnsupportedOperationException("Only classes being processed from source code can define associated beans");
    }

    /**
     * If {@link #isSuspend()} returns true this method exposes the continuation parameter in addition to the other parameters of the method.
     *
     * @return The suspend parameters
     * @since 2.3.0
     */
    default @NonNull ParameterElement[] getSuspendParameters() {
        return getParameters();
    }

    /**
     * Returns true if the method has parameters.
     *
     * @return True if it does
     */
    default boolean hasParameters() {
        return getParameters().length > 0;
    }

    /**
     * Is the method a Kotlin suspend function.
     *
     * @return True if it is.
     * @since 2.3.0
     */
    default boolean isSuspend() {
        return false;
    }

    /**
     * Is the method a default method on an interfaces.
     *
     * @return True if it is.
     * @since 2.3.0
     */
    default boolean isDefault() {
        return false;
    }

    /**
     * If method has varargs parameter.
     * @return True if it does
     * @since 4.0.0
     */
    default boolean isVarArgs() {
        return false;
    }

    /**
     * The generic return type of the method.
     *
     * @return The return type of the method
     * @since 1.1.1
     */
    default @NonNull ClassElement getGenericReturnType() {
        return getReturnType();
    }

    /**
     * Get the method description.
     *
     * @param simple If simple type names are to be used
     * @return The method description
     */
    @Override
    default @NonNull String getDescription(boolean simple) {
        String typeString = simple ? getReturnType().getSimpleName() : getReturnType().getName();
        String args = Arrays.stream(getParameters()).map(arg -> simple ? arg.getType().getSimpleName() : arg.getType().getName() + " " + arg.getName()).collect(Collectors.joining(","));
        return typeString + " " + getName() + "(" + args + ")";
    }

    /**
     * Checks if this method element overrides another.
     *
     * @param overridden Possible overridden method
     * @return true if this overrides passed method element
     * @since 3.1
     */
    default boolean overrides(@NonNull MethodElement overridden) {
        if (this.equals(overridden) || isStatic() || overridden.isStatic() || overridden.isFinal()) {
            return false;
        }
        // Cannot override existing private/package private methods even if the signature is the same
        if (overridden.isPrivate()) {
            return false;
        }
        MethodElement newMethod = this;
        if (overridden.isPackagePrivate() && !newMethod.getDeclaringType().getPackageName().equals(overridden.getDeclaringType().getPackageName())) {
            // Cannot override package-private from different packages
            return false;
        }
        ClassElement thisType = getDeclaringType();
        ClassElement thatType = overridden.getDeclaringType();
        if (thisType.getName().equals(thatType.getName())) {
            return false; // Same type
        }
        if (newMethod.isAbstract() && !newMethod.isDefault() && (!overridden.isAbstract() || overridden.isDefault())) {
            return false; // Abstract / default flags doesn't match
        }
        if (!newMethod.getName().equals(overridden.getName()) || overridden.getParameters().length != newMethod.getParameters().length) {
            return false; // Different method name or parameters not equal
        }
        if (!thisType.isAssignable(thatType)) { // More expensive check
            // not a parent class
            return false;
        }
        for (int i = 0; i < overridden.getParameters().length; i++) {
            ParameterElement existingParameter = overridden.getParameters()[i];
            ParameterElement newParameter = newMethod.getParameters()[i];
            ClassElement existingType = existingParameter.getGenericType();
            ClassElement newType = newParameter.getGenericType();
            if (!newType.isAssignable(existingType)) {
                return false;
            }
        }
        ClassElement existingReturnType = overridden.getReturnType().getGenericType();
        ClassElement newTypeReturn = newMethod.getReturnType().getGenericType();
        return newTypeReturn.isAssignable(existingReturnType);
    }

    @Override
    default boolean hides(@NonNull MemberElement memberElement) {
        if (memberElement instanceof MethodElement hidden) {
            if (equals(hidden) || isStatic() || hidden.isStatic() || hidden.isPrivate()) {
                return false;
            }
            MethodElement newMethod = this;
            if (!newMethod.getName().equals(hidden.getName()) || hidden.getParameters().length != newMethod.getParameters().length) {
                return false;
            }
            for (int i = 0; i < hidden.getParameters().length; i++) {
                ParameterElement existingParameter = hidden.getParameters()[i];
                ParameterElement newParameter = newMethod.getParameters()[i];
                ClassElement existingType = existingParameter.getGenericType();
                ClassElement newType = newParameter.getGenericType();
                if (!newType.isAssignable(existingType)) {
                    return false;
                }
            }
            ClassElement existingReturnType = hidden.getReturnType().getGenericType();
            ClassElement newTypeReturn = newMethod.getReturnType().getGenericType();
            if (!newTypeReturn.isAssignable(existingReturnType)) {
                return false;
            }
            if (hidden.isPackagePrivate()) {
                return newMethod.getDeclaringType().getPackageName().equals(hidden.getDeclaringType().getPackageName());
            }
            return true;
        }
        return false;
    }

    /**
     * Creates a {@link MethodElement} for the given parameters.
     *
     * @param declaredType       The declaring type
     * @param annotationMetadata The annotation metadata
     * @param returnType         The return type
     * @param genericReturnType  The generic return type
     * @param name               The name
     * @param parameterElements  The parameter elements
     * @return The method element
     */
    static @NonNull MethodElement of(
        @NonNull ClassElement declaredType,
        @NonNull AnnotationMetadata annotationMetadata,
        @NonNull ClassElement returnType,
        @NonNull ClassElement genericReturnType,
        @NonNull String name,
        ParameterElement... parameterElements) {
        return new MethodElement() {

            @Override
            public boolean isSynthetic() {
                return true;
            }

            @NonNull
            @Override
            public ClassElement getReturnType() {
                return returnType;
            }

            @NonNull
            @Override
            public ClassElement getGenericReturnType() {
                return genericReturnType;
            }

            @Override
            public ParameterElement[] getParameters() {
                return parameterElements;
            }

            @Override
            public MethodElement withParameters(ParameterElement... newParameters) {
                return MethodElement.of(
                    declaredType,
                    annotationMetadata,
                    returnType,
                    genericReturnType,
                    name,
                    newParameters
                );
            }

            @NonNull
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return annotationMetadata;
            }

            @Override
            public ClassElement getDeclaringType() {
                return declaredType;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isPackagePrivate() {
                return false;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return true;
            }

            @NonNull
            @Override
            public Object getNativeType() {
                throw new UnsupportedOperationException("No native method type present");
            }

            @Override
            public String toString() {
                return getDeclaringType().getName() + "." + name + "(..)";
            }
        };
    }

    /**
     * Creates a {@link MethodElement} for the given parameters.
     *
     * @param owningType                       The owing type
     * @param declaringType                    The declaring type
     * @param methodAnnotationMetadataProvider The method annotation metadata provider
     * @param annotationMetadataProvider       The annotation metadata provider
     * @param metadataBuilder                  The metadata builder
     * @param returnType                       The return type
     * @param genericReturnType                The generic return type
     * @param name                             The name
     * @param isStatic                         Is static
     * @param isFinal                          Is final
     * @param parameterElements                The parameter elements
     * @return The method element
     * @since 4.0.0
     */
    static @NonNull MethodElement of(
        @NonNull ClassElement owningType,
        @NonNull ClassElement declaringType,
        @NonNull AnnotationMetadataProvider methodAnnotationMetadataProvider,
        @NonNull AnnotationMetadataProvider annotationMetadataProvider,
        @NonNull AbstractAnnotationMetadataBuilder<?, ?> metadataBuilder,
        @NonNull ClassElement returnType,
        @NonNull ClassElement genericReturnType,
        @NonNull String name,
        boolean isStatic,
        boolean isFinal,
        ParameterElement... parameterElements) {
        return new MethodElement() {

            private @Nullable AnnotationMetadata methodAnnotationMetadata;
            private @Nullable AnnotationMetadata annotationMetadata;

            @Override
            public boolean isSynthetic() {
                return true;
            }

            @NonNull
            @Override
            public ClassElement getReturnType() {
                return returnType;
            }

            @NonNull
            @Override
            public ClassElement getGenericReturnType() {
                return genericReturnType;
            }

            @Override
            public ParameterElement[] getParameters() {
                return parameterElements;
            }

            @Override
            public MethodElement withParameters(ParameterElement... newParameters) {
                return MethodElement.of(
                    owningType,
                    declaringType,
                    new AnnotationMetadataProvider() {
                        @Override
                        public AnnotationMetadata getAnnotationMetadata() {
                            return methodAnnotationMetadata;
                        }
                    },
                    new AnnotationMetadataProvider() {
                        @Override
                        public AnnotationMetadata getAnnotationMetadata() {
                            return annotationMetadata;
                        }
                    },
                    metadataBuilder,
                    returnType,
                    genericReturnType,
                    name,
                    isStatic,
                    isFinal,
                    newParameters
                );
            }

            @Override
            public MutableAnnotationMetadataDelegate<AnnotationMetadata> getMethodAnnotationMetadata() {
                return new MutableAnnotationMetadataDelegate<>() {

                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return getMethodAnnotationMetadata0();
                    }
                };
            }

            private AnnotationMetadata getMethodAnnotationMetadata0() {
                if (methodAnnotationMetadata == null) {
                    methodAnnotationMetadata = methodAnnotationMetadataProvider.getAnnotationMetadata().copyAnnotationMetadata();
                }
                return methodAnnotationMetadata;
            }

            @NonNull
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                if (annotationMetadata == null) {
                    annotationMetadata = annotationMetadataProvider.getAnnotationMetadata().copyAnnotationMetadata();
                }
                return annotationMetadata;
            }

            @Override
            public ClassElement getOwningType() {
                return owningType;
            }

            @Override
            public ClassElement getDeclaringType() {
                return declaringType;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isPackagePrivate() {
                return false;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return true;
            }

            @Override
            public boolean isStatic() {
                return isStatic;
            }

            @Override
            public boolean isFinal() {
                return isFinal;
            }

            @Override
            @SuppressWarnings("java:S1192")
            public <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
                //noinspection ConstantConditions
                if (consumer != null) {

                    consumer.accept(builder);
                    AnnotationValue<T> av = builder.build();
                    this.methodAnnotationMetadata = metadataBuilder.annotate(getMethodAnnotationMetadata0(), av);
                    this.annotationMetadata = metadataBuilder.annotate(getAnnotationMetadata(), av);
                }
                return this;
            }

            @Override
            public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
                ArgumentUtils.requireNonNull("annotationValue", annotationValue);
                methodAnnotationMetadata = metadataBuilder.annotate(getMethodAnnotationMetadata0(), annotationValue);
                annotationMetadata = metadataBuilder.annotate(getAnnotationMetadata(), annotationValue);
                return this;
            }

            @Override
            @SuppressWarnings("java:S1192")
            public Element removeAnnotation(@NonNull String annotationType) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                methodAnnotationMetadata = metadataBuilder.removeAnnotation(getMethodAnnotationMetadata0(), annotationType);
                annotationMetadata = metadataBuilder.removeAnnotation(getAnnotationMetadata(), annotationType);
                return this;
            }

            @Override
            public <T extends Annotation> Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
                ArgumentUtils.requireNonNull("predicate", predicate);
                methodAnnotationMetadata = metadataBuilder.removeAnnotationIf(getMethodAnnotationMetadata0(), predicate);
                annotationMetadata = metadataBuilder.removeAnnotationIf(getAnnotationMetadata(), predicate);
                return this;

            }

            @Override
            @SuppressWarnings("java:S1192")
            public Element removeStereotype(@NonNull String annotationType) {
                ArgumentUtils.requireNonNull("annotationType", annotationType);
                methodAnnotationMetadata = metadataBuilder.removeStereotype(getMethodAnnotationMetadata0(), annotationType);
                annotationMetadata = metadataBuilder.removeStereotype(getAnnotationMetadata(), annotationType);
                return this;
            }

            @NonNull
            @Override
            public Object getNativeType() {
                throw new UnsupportedOperationException("No native method type present");
            }

            @Override
            public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
                return MethodElement.of(owningType,
                    declaringType,
                    methodAnnotationMetadataProvider, new AnnotationMetadataProvider() {
                        @Override
                        public AnnotationMetadata getAnnotationMetadata() {
                            return annotationMetadata;
                        }
                    },
                    metadataBuilder,
                    returnType, genericReturnType, name, isStatic, isFinal, parameterElements);
            }

            @Override
            public String toString() {
                return getDeclaringType().getName() + "." + name + "(..)";
            }
        };
    }

    @Override
    default MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MethodElement) MemberElement.super.withAnnotationMetadata(annotationMetadata);
    }
}
