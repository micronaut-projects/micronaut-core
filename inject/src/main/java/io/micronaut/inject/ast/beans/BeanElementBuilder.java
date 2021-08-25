/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast.beans;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interface for defining beans at compilation time from an originating element.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Experimental
public interface BeanElementBuilder extends ConfigurableElement {

    /**
     * @return The originating element
     */
    @NonNull
    Element getOriginatingElement();

    /**
     * @return The bean type
     */
    @NonNull
    ClassElement getBeanType();

    /**
     * @return The element that produces the bean.
     */
    @NonNull
    default Element getProducingElement() {
        return getBeanType();
    }

    /**
     * Returns the class that declares the bean. In case of a bean defined by a class, that is the bean class directly. In case of a producer method or field, that is the class that declares the producer method or field.
     *
     * @return The element declares the bean.
     */
    @NonNull
    default ClassElement getDeclaringElement() {
        return getBeanType();
    }

    /**
     * Specifies the bean will created with the given method element. If
     * not specified the bean will be created with {@link ClassElement#getPrimaryConstructor()}.
     *
     * <p>Note that the method can be a one of the following:</p>
     *
     * <ul>
     *     <li>An accessible constructor on the bean type being generated</li>
     *     <li>An accessible static method on the bean type being generated</li>
     * </ul>
     *
     * @param element The element
     * @return This bean builder
     */
    @NonNull
    BeanElementBuilder createWith(@NonNull MethodElement element);

    /**
     * Alters the exposed types for the bean limiting the exposed type to the given types.
     * @param types 1 or more types to expose
     * @return This builder
     */
    @NonNull
    BeanElementBuilder typed(ClassElement... types);

    /**
     * Fills the type arguments for the bean with the given types.
     * @param types The types
     * @return This bean builder
     */
    @Override
    @NonNull
    BeanElementBuilder typeArguments(@NonNull ClassElement... types);

    /**
     * Fills the type arguments for the given interface or super class with the given types.
     * @param type The type or interface. If null, results in a no-op
     * @param types The types
     * @return This bean builder
     */
    @NonNull
    BeanElementBuilder typeArgumentsForType(@Nullable ClassElement type, @NonNull ClassElement... types);

    /**
     * Adds a scope for the given annotation value to the bean.
     *
     * @param scope The scope
     * @return This bean element builder
     */
    default @NonNull
    BeanElementBuilder scope(@NonNull AnnotationValue<?> scope) {
        Objects.requireNonNull(scope, "Scope cannot be null");
        annotate(scope.getAnnotationName(), (builder) -> builder.members(scope.getValues()));
        return this;
    }

    /**
     * Adds a scope for the given annotation value to the bean.
     *
     * @param scope The full qualified scope annotation name
     * @return This bean element builder
     */
    default @NonNull
    BeanElementBuilder scope(@NonNull String scope) {
        Objects.requireNonNull(scope, "Scope cannot be null");
        annotate(scope);
        return this;
    }

    /**
     * Allows configuring the bean constructor.
     * @param constructorElement The constructor element
     * @return This bean builder
     */
    @NonNull
    BeanElementBuilder withConstructor(@NonNull Consumer<BeanConstructorElement> constructorElement);

    /**
     * Allows configuring methods of the bean.
     * @param methods The {@link ElementQuery} to locate selected methods.
     * @param beanMethods A consumer that receives each {@link BeanMethodElement}
     * @return This builder
     */
    @NonNull
    BeanElementBuilder withMethods(
            @NonNull ElementQuery<MethodElement> methods,
            @NonNull Consumer<BeanMethodElement> beanMethods);

    /**
     * Allows configuring fields of the bean.
     * @param fields The {@link ElementQuery} to locate fields.
     * @param beanFields The bean fields
     * @return This builder
     */
    @NonNull
    BeanElementBuilder withFields(
            @NonNull ElementQuery<FieldElement> fields,
            @NonNull Consumer<BeanFieldElement> beanFields);

    /**
     * Allows configuring the parameters for the current constructor.
     * @param parameters The parameters
     * @return This builder
     */
    @NonNull
    BeanElementBuilder withParameters(Consumer<BeanParameterElement[]> parameters);

    @NonNull
    @Override
    default BeanElementBuilder qualifier(@Nullable String qualifier) {
        return (BeanElementBuilder) ConfigurableElement.super.qualifier(qualifier);
    }

    @NonNull
    @Override
    default BeanElementBuilder qualifier(@NonNull AnnotationValue<?> qualifier) {
        return (BeanElementBuilder) ConfigurableElement.super.qualifier(qualifier);
    }

    @NonNull
    @Override
    default <T extends Annotation> BeanElementBuilder annotate(@NonNull String annotationType,
                                                               @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        return (BeanElementBuilder) ConfigurableElement.super.annotate(annotationType, consumer);
    }

    @Override
    default BeanElementBuilder removeAnnotation(@NonNull String annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.removeAnnotation(annotationType);
    }

    @Override
    default <T extends Annotation> BeanElementBuilder removeAnnotation(@NonNull Class<T> annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.removeAnnotation(annotationType);
    }

    @Override
    default <T extends Annotation> BeanElementBuilder removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        return (BeanElementBuilder) ConfigurableElement.super.removeAnnotationIf(predicate);
    }

    @Override
    default BeanElementBuilder removeStereotype(@NonNull String annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.removeStereotype(annotationType);
    }

    @Override
    default <T extends Annotation> BeanElementBuilder removeStereotype(@NonNull Class<T> annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.removeStereotype(annotationType);
    }

    @NonNull
    @Override
    default BeanElementBuilder annotate(@NonNull String annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.annotate(annotationType);
    }

    @NonNull
    @Override
    default <T extends Annotation> BeanElementBuilder annotate(@NonNull Class<T> annotationType,
                                                               @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        return (BeanElementBuilder) ConfigurableElement.super.annotate(annotationType, consumer);
    }

    @NonNull
    @Override
    default <T extends Annotation> BeanElementBuilder annotate(@NonNull Class<T> annotationType) {
        return (BeanElementBuilder) ConfigurableElement.super.annotate(annotationType);
    }

    /**
     * Dependency inject this bean.
     * @return this bean builder
     */
    BeanElementBuilder inject();

    /**
     * Produce additional beans from the given methods.
     * @param methodsOrFields The {@link io.micronaut.inject.ast.ElementQuery} representing the methods or fields
     * @param childBeanBuilder Configure the child bean builder
     * @return This bean builder
     * @param <E> A type variable to
     */
    <E extends MemberElement> BeanElementBuilder produceBeans(ElementQuery<E> methodsOrFields, @Nullable Consumer<BeanElementBuilder> childBeanBuilder);

    /**
     * Produce additional beans from the given methods.
     * @param methodsOrFields The {@link io.micronaut.inject.ast.ElementQuery} representing the methods or fields
     * @return This bean builder
     * @param <E> A type variable to
     */
    default <E extends MemberElement> BeanElementBuilder produceBeans(ElementQuery<E> methodsOrFields) {
        return produceBeans(methodsOrFields, null);
    }
}