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
package io.micronaut.inject;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.type.TypeInformation.TypeFormat;
import io.micronaut.core.util.AnsiColour;
import io.micronaut.inject.proxy.InterceptedBean;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.micronaut.core.annotation.AnnotationUtil.ANN_ADAPTER;
import static io.micronaut.core.type.TypeInformation.TypeFormat.getBeanTypeString;

/**
 * Defines a bean definition and its requirements. A bean definition must have a singled injectable constructor or a
 * no-args constructor.
 *
 * @param <T> The bean type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinition<T> extends QualifiedBeanType<T>, Named, BeanType<T>, ArgumentCoercible<T> {

    /**
     * @return The type information for the bean.
     * @since 4.8.0
     */
    default @NonNull TypeInformation<T> getTypeInformation() {
        return new TypeInformation<>() {
            @Override
            public String getBeanTypeString(TypeFormat format) {
                Class<T> beanType = getType();
                boolean synthetic = beanType.isSynthetic();
                if (synthetic) {
                    AnnotationMetadata annotationMetadata = getAnnotationMetadata();
                    // synthetic bean so produce better formatting.
                    if (annotationMetadata.hasDeclaredStereotype(ANN_ADAPTER)) {
                        @SuppressWarnings("unchecked") ExecutableMethod<Object, ?> method =
                            (ExecutableMethod<Object, ?>) BeanDefinition.this.getExecutableMethods().iterator().next();
                        // Not great, but to produce accurate debug output we have to reach into AOP internals
                        Class<?> resolvedBeanType = method.classValue(ANN_ADAPTER, "adaptedBean")
                            .orElse(beanType);
                        return TypeFormat.getBeanTypeString(
                            format,
                            resolvedBeanType,
                            getGenericBeanType().getTypeVariables(),
                            annotationMetadata
                        );
                    } else if (InterceptedBean.class.isAssignableFrom(beanType)) {
                        if (beanType.isInterface()) {
                            return TypeFormat.getBeanTypeString(
                                format,
                                beanType.getInterfaces()[0],
                                getGenericBeanType().getTypeVariables(),
                                annotationMetadata
                            );
                        } else {
                            return TypeFormat.getBeanTypeString(
                                format,
                                beanType.getSuperclass(),
                                getGenericBeanType().getTypeVariables(),
                                annotationMetadata
                            );
                        }
                    } else {
                        return TypeInformation.super.getBeanTypeString(format);
                    }
                } else {
                    return TypeInformation.super.getBeanTypeString(format);
                }
            }

            @Override
            public Class<T> getType() {
                return getBeanType();
            }

            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return BeanDefinition.this.getGenericBeanType().getTypeVariables();
            }

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return BeanDefinition.this.getAnnotationMetadata();
            }
        };
    }

    /**
     * @return The scope of the bean
     */
    default Optional<Class<? extends Annotation>> getScope() {
        return Optional.empty();
    }

    /**
     * @return The name of the scope
     */
    default Optional<String> getScopeName() {
        return Optional.empty();
    }

    /**
     * @return Whether the scope is singleton
     */
    default boolean isSingleton() {
        final String scopeName = getScopeName().orElse(null);
        if (scopeName != null && scopeName.equals(AnnotationUtil.SINGLETON)) {
            return true;
        } else {
            return getAnnotationMetadata().stringValue(DefaultScope.class)
                    .map(t -> t.equals(Singleton.class.getName()) || t.equals(AnnotationUtil.SINGLETON))
                    .orElse(false);
        }
    }

    /**
     * If {@link #isContainerType()} returns true this will return the container element.
     * @return The container element.
     */
    default Optional<Argument<?>> getContainerElement() {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("java:S3776")
    default boolean isCandidateBean(@Nullable Argument<?> beanType) {
        if (beanType == null) {
            return false;
        }
        if (QualifiedBeanType.super.isCandidateBean(beanType)) {
            final Argument<?>[] typeArguments = beanType.getTypeParameters();
            final int len = typeArguments.length;
            Class<?> beanClass = beanType.getType();
            if (len == 0) {
                if (isContainerType()) {
                    if (getBeanType().isAssignableFrom(beanClass)) {
                        return true;
                    }
                    final Optional<Argument<?>> containerElement = getContainerElement();
                    if (containerElement.isPresent()) {
                        final Class<?> t = containerElement.get().getType();
                        return beanType.isAssignableFrom(t) || beanClass == t;
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            } else {
                final Argument<?>[] beanTypeParameters;
                if (!Iterable.class.isAssignableFrom(beanClass)) {
                    final Optional<Argument<?>> containerElement = getContainerElement();
                    //noinspection OptionalIsPresent
                    if (containerElement.isPresent()) {
                        beanTypeParameters = containerElement.get().getTypeParameters();
                    } else {
                        beanTypeParameters = getTypeArguments(beanClass).toArray(Argument.ZERO_ARGUMENTS);
                    }
                } else {
                    beanTypeParameters = getTypeArguments(beanClass).toArray(Argument.ZERO_ARGUMENTS);
                }
                if (len != beanTypeParameters.length) {
                    return false;
                }

                for (int i = 0; i < beanTypeParameters.length; i++) {
                    Argument<?> candidateParameter = beanTypeParameters[i];
                    final Argument<?> requestedParameter = typeArguments[i];
                    if (!requestedParameter.isAssignableFrom(candidateParameter.getType())) {
                        if (!(candidateParameter.isTypeVariable() && candidateParameter.isAssignableFrom(requestedParameter.getType()))) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @return Whether the bean declared with {@link io.micronaut.context.annotation.EachProperty} or
     * {@link io.micronaut.context.annotation.EachBean}
     */
    default boolean isIterable() {
        return hasDeclaredStereotype(EachProperty.class) || hasDeclaredStereotype(EachBean.class);
    }

    /**
     * @return Is the type configuration properties.
     */
    default boolean isConfigurationProperties() {
        return isIterable() || hasDeclaredStereotype(ConfigurationReader.class);
    }

    /**
     * @return The produced bean type
     */
    @Override
    Class<T> getBeanType();

    /**
     * @return The type that declares this definition, null if not applicable.
     */
    default Optional<Class<?>> getDeclaringType() {
        return Optional.empty();
    }

    /**
     * The single concrete constructor that is an injection point for creating the bean.
     *
     * @return The constructor injection point
     */
    default ConstructorInjectionPoint<T> getConstructor() {
        return new ConstructorInjectionPoint<>() {

            @Override
            public Argument<?>[] getArguments() {
                return Argument.ZERO_ARGUMENTS;
            }

            @Override
            public BeanDefinition<T> getDeclaringBean() {
                return BeanDefinition.this;
            }

            @Override
            public String toString() {
                return getDeclaringBeanType().getName() + "(" + Argument.toString(getArguments()) + ")";
            }
        };
    }

    /**
     * @return All required components for this entity definition
     */
    default Collection<Class<?>> getRequiredComponents() {
        return Collections.emptyList();
    }

    /**
     * All methods that require injection. This is a subset of all the methods in the class.
     *
     * @return The required properties
     */
    default Collection<MethodInjectionPoint<T, ?>> getInjectedMethods() {
        return Collections.emptyList();
    }

    /**
     * All the fields that require injection.
     *
     * @return The required fields
     */
    default Collection<FieldInjectionPoint<T, ?>> getInjectedFields() {
        return Collections.emptyList();
    }

    /**
     * All the methods that should be called once the bean has been fully initialized and constructed.
     *
     * @return Methods to call post construct
     */
    default Collection<MethodInjectionPoint<T, ?>> getPostConstructMethods() {
        return Collections.emptyList();
    }

    /**
     * All the methods that should be called when the object is to be destroyed.
     *
     * @return Methods to call pre-destroy
     */
    default Collection<MethodInjectionPoint<T, ?>> getPreDestroyMethods() {
        return Collections.emptyList();
    }

    /**
     * @return The class name
     */
    @Override
    @NonNull
    default String getName() {
        return getBeanType().getName();
    }

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     */
    default <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        return Optional.empty();
    }

    /**
     * Whether an {@link ExecutableMethod} exists which is annotated with the supplied annotation
     *
     * @param methodName          The method name
     * @param argumentTypes The argument types
     * @return Whether an {@link ExecutableMethod} exists which is annotated with the supplied annotation
     * @since 4.3.0
     */
    default boolean hasAnnotatedMethod(@NonNull Class<? extends Annotation> annotationClass,
                                       @NonNull String methodName,
                                       @NonNull Class<?>... argumentTypes) {
        return findMethod(methodName, argumentTypes).map(method -> method.hasAnnotation(annotationClass)).orElse(false);
    }

    /**
     * Finds possible methods for the given method name.
     *
     * @param name The method name
     * @param <R>  The return type
     * @return The possible methods
     */
    default <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        return Stream.empty();
    }

    /**
     * @return The {@link ExecutableMethod} instances for this definition
     */
    default Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    default Argument<T> asArgument() {
        return Argument.of(
                getBeanType(),
                getAnnotationMetadata(),
                getTypeParameters()
        );
    }

    /**
     * Whether this bean definition represents a proxy.
     *
     * @return True if it represents a proxy
     */
    default boolean isProxy() {
        return false;
    }

    /**
     * If the bean itself declares any type arguments this method will return the classes that represent those types.
     *
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments() {
        return getTypeArguments(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == null) {
            return Collections.emptyList();
        }
        return getTypeArguments(type.getName());
    }

    /**
     * Returns the type parameters as a class array for the given type.
     * @param type The type
     * @return The type parameters
     */
    default @NonNull Class<?>[] getTypeParameters(@Nullable Class<?> type) {
        if (type == null) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        } else {
            final List<Argument<?>> typeArguments = getTypeArguments(type);
            if (typeArguments.isEmpty()) {
                return ReflectionUtils.EMPTY_CLASS_ARRAY;
            }
            Class<?>[] params = new Class<?>[typeArguments.size()];
            int i = 0;
            for (Argument<?> argument : typeArguments) {
                params[i++] = argument.getType();
            }
            return params;
        }
    }

    /**
     *
     * Returns the type parameters as a class array for the bean type.
     *
     * @return The type parameters for the bean type as a class array.
     */
    default @NonNull Class<?>[] getTypeParameters() {
        return getTypeParameters(getBeanType());
    }

    /**
     * Return the type arguments for the given interface or super type for this bean.
     *
     * @param type The super class or interface type
     * @return The type arguments
     */
    default @NonNull List<Argument<?>> getTypeArguments(String type) {
        return Collections.emptyList();
    }

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     * @throws IllegalStateException If the method cannot be found
     */
    @SuppressWarnings("unchecked")
    default <R> ExecutableMethod<T, R> getRequiredMethod(String name, Class<?>... argumentTypes) {
        return (ExecutableMethod<T, R>) findMethod(name, argumentTypes)
            .orElseThrow(() -> ReflectionUtils.newNoSuchMethodError(getBeanType(), name, argumentTypes));
    }

    /**
     * @return Whether the bean definition is abstract
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(getBeanType().getModifiers());
    }

    @Override
    default Argument<T> getGenericBeanType() {
        return asArgument();
    }

    /**
     * Resolve the declared qualifier for this bean.
     * @return The qualifier or null if this isn't one
     */
    @Override
    default @Nullable Qualifier<T> getDeclaredQualifier() {
        return QualifiedBeanType.super.getDeclaredQualifier();
    }

    /**
     * Gets a description of the bean as close as possible to source representation.
     * @param typeFormat The type format to use.
     * @param includeArguments Whether to include arguments.
     * @return The bean description.
     * @since 4.8.0
     */
    default @NonNull String getBeanDescription(@NonNull TypeFormat typeFormat, boolean includeArguments) {
        ConstructorInjectionPoint<T> constructor = getConstructor();
        StringBuilder beanDescription = new StringBuilder();
        Argument<?>[] arguments = constructor.getArguments();
        if (constructor instanceof MethodInjectionPoint<?,?> methodInjectionPoint) {
            // factory bean with method
            Class<?> declaringType = methodInjectionPoint.getDeclaringType();
            Class<T> declaringBeanType = constructor.getDeclaringBeanType();
            String factoryType = TypeFormat.getTypeString(
                typeFormat,
                declaringBeanType,
                Map.of()
            );
            String beanTypeName = getBeanTypeString(
                typeFormat,
                declaringType,
                asArgument().getTypeVariables(),
                methodInjectionPoint.getAnnotationMetadata()
            );

            beanDescription.append(beanTypeName).append(" ");
            beanDescription.append(factoryType)
                .append(".")
                .append(methodInjectionPoint.getName());
        } else if (constructor instanceof FieldInjectionPoint<?,?> fieldInjectionPoint) {
            // factory bean with method
            Class<T> declaringBeanType = constructor.getDeclaringBeanType();
            String factoryType = TypeFormat.getTypeString(
                typeFormat,
                declaringBeanType,
                Map.of()
            );
            Class<?> declaringType = fieldInjectionPoint.getDeclaringBean().getBeanType();
            String beanTypeName = getBeanTypeString(
                typeFormat,
                declaringType,
                asArgument().getTypeVariables(),
                fieldInjectionPoint.getAnnotationMetadata()
            );
            beanDescription.append(beanTypeName).append(" ");
            beanDescription.append(factoryType)
                .append(".")
                .append(fieldInjectionPoint.getName());
            return beanDescription.toString();
        } else {
            boolean synthetic = getBeanType().isSynthetic();
            if (synthetic) {
                // AOP proxy or generated event listener
                AnnotationMetadata annotationMetadata = getAnnotationMetadata();
                if (annotationMetadata.hasDeclaredStereotype(ANN_ADAPTER)) {
                    @SuppressWarnings("unchecked") ExecutableMethod<Object, ?> method =
                        (ExecutableMethod<Object, ?>) getExecutableMethods().iterator().next();
                    // Not great, but to produce accurate debug output we have to reach into AOP internals
                    Class<?> adaptedType = method.classValue(ANN_ADAPTER).orElse(getBeanType());
                    Class<?> beanType = method.classValue(ANN_ADAPTER, "adaptedBean").orElse(getBeanType());
                    String beanMethod = method.stringValue(ANN_ADAPTER, "adaptedMethod").orElse("unknown");
                    String beanTypeString = getBeanTypeString(
                        typeFormat,
                        beanType,
                        asArgument().getTypeVariables(),
                        annotationMetadata
                    );
                    beanDescription.append(beanTypeString)
                        .append(".")
                        .append(beanMethod);
                    @NonNull Argument<?>[] methodArguments = method.getArguments();
                    List<Argument<?>> typeArguments = getTypeArguments(adaptedType);
                    if (typeArguments.size() == methodArguments.length) {
                        arguments = new Argument[methodArguments.length];
                        for (int i = 0; i < methodArguments.length; i++) {
                            @NonNull Argument<?> methodArgument = methodArguments[i];
                            Argument<?> t = typeArguments.get(i);
                            arguments[i] = Argument.of(
                                t.getType(),
                                methodArgument.getName(),
                                methodArgument.getAnnotationMetadata(),
                                t.getTypeParameters()
                            );
                        }
                    }
                } else {
                    Class<T> beanType = getBeanType();
                    String beanTypeString;
                    if (beanType.isInterface()) {
                        beanTypeString = getBeanTypeString(
                            typeFormat,
                            beanType.getInterfaces()[0],
                            asArgument().getTypeVariables(),
                            annotationMetadata
                        );
                    } else {
                        beanTypeString = getBeanTypeString(
                            typeFormat,
                            beanType.getSuperclass(),
                            asArgument().getTypeVariables(),
                            annotationMetadata
                        );
                    }
                    beanDescription.append(beanTypeString);
                }
            } else {
                beanDescription.append(
                    getTypeInformation().getBeanTypeString(typeFormat)
                );
            }
        }

        if (includeArguments) {
            beanDescription.append(typeFormat.isAnsi() ? AnsiColour.brightCyan("(") : "(");

            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                if (argument.getName().startsWith("$")) {
                    // skip internal
                    continue;
                }
                String argType = getBeanTypeString(
                    typeFormat,
                    argument
                );
                String argumentName = argument.getName();
                beanDescription.append(argType)
                    .append(" ")
                    .append(typeFormat.isAnsi() ? AnsiColour.brightBlue(argumentName) : argumentName);

                if (i != arguments.length - 1) {
                    Argument<?> next = arguments[i + 1];
                    if (getBeanType().isSynthetic() &&
                        next.getName().startsWith("$")) {
                        // skip synthetic arguments
                        break;
                    }
                    beanDescription.append(", ");
                }
            }

            beanDescription.append(typeFormat.isAnsi() ? AnsiColour.brightCyan(")") : ")");
        }
        return beanDescription.toString();
    }

    /**
     * Gets a description of the bean as close as possible to source representation.
     * @param typeFormat The type format to use.
     * @return The bean description.
     * @since 4.8.0
     */
    default @NonNull String getBeanDescription(@NonNull TypeFormat typeFormat) {
        return getBeanDescription(typeFormat, true);
    }
}
