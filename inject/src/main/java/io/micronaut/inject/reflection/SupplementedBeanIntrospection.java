/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.reflection;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A generated {@link BeanIntrospection} completed by a reflective one: the properties, the constructor and
 * the methods the processor generated are served as generated, and the executables it left out — the
 * methods that are not {@code @Executable}, the other constructors — come from reflection.
 *
 * <p>A processor generates what the application needs; a specification describing the type needs all of
 * it. This keeps one introspection per type, generated where it exists.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class SupplementedBeanIntrospection<T> implements ReflectiveIntrospection<T> {

    private final BeanIntrospection<T> generated;
    private final ReflectionBeanIntrospection<T> reflected;
    private final List<BeanMethod<T, Object>> methods;

    /**
     * @param generated The generated introspection
     * @param reflected The reflective introspection of the same type
     */
    public SupplementedBeanIntrospection(BeanIntrospection<T> generated, ReflectionBeanIntrospection<T> reflected) {
        this.generated = generated;
        this.reflected = reflected;
        List<BeanMethod<T, Object>> merged = new ArrayList<>();
        for (BeanMethod<T, Object> method : generated.getBeanMethods()) {
            // the generated method, with the class the reflection knows declares it
            BeanMethod<T, Object> reflectedMethod = findMethod(new ArrayList<>(reflected.getBeanMethods()), method.getName(), Argument.toClassArray(method.getArguments()));
            merged.add(reflectedMethod == null ? method : new DeclaredBeanMethod<>(method, reflectedMethod.getDeclaringType()));
        }
        for (BeanMethod<T, Object> method : reflected.getBeanMethods()) {
            if (findMethod(merged, method.getName(), Argument.toClassArray(method.getArguments())) == null) {
                merged.add(method);
            }
        }
        this.methods = Collections.unmodifiableList(merged);
    }

    /**
     * @return The generated introspection
     */
    public BeanIntrospection<T> getGenerated() {
        return generated;
    }

    @Override
    public Class<T> getBeanType() {
        return generated.getBeanType();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return generated.getAnnotationMetadata();
    }

    @Override
    public Collection<BeanProperty<T, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) {
        return generated.getIndexedProperties(annotationType);
    }

    @Override
    public Optional<BeanProperty<T, Object>> getIndexedProperty(Class<? extends Annotation> annotationType, String annotationValue) {
        return generated.getIndexedProperty(annotationType, annotationValue);
    }

    @Override
    public Collection<BeanMethod<T, Object>> getBeanMethods() {
        return methods;
    }

    @Override
    public Optional<BeanMethod<T, Object>> findDeclaredMethod(String name, Class<?>... parameterTypes) {
        return reflected.findDeclaredMethod(name, parameterTypes);
    }

    @Override
    public Argument<?>[] getConstructorArguments() {
        return generated.getConstructorArguments();
    }

    /**
     * The generated constructor, with the metadata the reflection reads from the constructor when the
     * generated one carries none: a generated introspection instantiates, it does not describe.
     */
    @Override
    public BeanConstructor<T> getConstructor() {
        BeanConstructor<T> constructor = generated.getConstructor();
        if (!constructor.getAnnotationMetadata().isEmpty()) {
            return constructor;
        }
        Class<?>[] generatedTypes = Argument.toClassArray(generated.getConstructorArguments());
        for (BeanConstructor<T> reflectedConstructor : reflected.getConstructors()) {
            if (Arrays.equals(Argument.toClassArray(reflectedConstructor.getArguments()), generatedTypes)) {
                return new DescribedBeanConstructor<>(constructor, reflectedConstructor);
            }
        }
        return constructor;
    }

    @Override
    public List<BeanConstructor<T>> getConstructors() {
        List<BeanConstructor<T>> constructors = new ArrayList<>();
        constructors.add(getConstructor());
        Class<?>[] generatedTypes = Argument.toClassArray(generated.getConstructorArguments());
        for (BeanConstructor<T> constructor : reflected.getConstructors()) {
            if (!Arrays.equals(Argument.toClassArray(constructor.getArguments()), generatedTypes)) {
                constructors.add(constructor);
            }
        }
        return Collections.unmodifiableList(constructors);
    }

    /**
     * The generated properties, each with the type arguments the reflection reads from every member:
     * the getter of an interface can declare {@code Map<String, @NotBlank String>} where the field is a
     * plain {@code Map<String, String>}.
     */
    @Override
    public Collection<BeanProperty<T, Object>> getBeanProperties() {
        List<BeanProperty<T, Object>> properties = new ArrayList<>();
        for (BeanProperty<T, Object> property : generated.getBeanProperties()) {
            BeanProperty<T, Object> reflectedProperty = reflected.getProperty(property.getName()).orElse(null);
            properties.add(reflectedProperty == null ? property : new DescribedBeanProperty<>(property, reflectedProperty.asArgument()));
        }
        return Collections.unmodifiableList(properties);
    }

    @Override
    public Optional<BeanProperty<T, Object>> getProperty(String name) {
        for (BeanProperty<T, Object> property : getBeanProperties()) {
            if (property.getName().equals(name)) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PropertyMember> getPropertyMembers(String propertyName) {
        return reflected.getPropertyMembers(propertyName);
    }

    @Override
    public T instantiate() throws InstantiationException {
        return generated.instantiate();
    }

    @Override
    public T instantiate(boolean strictNullable, @Nullable Object... arguments) throws InstantiationException {
        return generated.instantiate(strictNullable, arguments);
    }

    @Override
    public boolean isBuildable() {
        return generated.isBuildable();
    }

    @Override
    public boolean hasBuilder() {
        return generated.hasBuilder();
    }

    @Override
    public Builder<T> builder() {
        return generated.builder();
    }

    @Override
    public String toString() {
        return "SupplementedBeanIntrospection(" + generated + ")";
    }

    /**
     * A generated constructor with the metadata of the reflective one.
     *
     * @param <B> The bean type
     */
    private static final class DescribedBeanConstructor<B> implements BeanConstructor<B> {

        private final BeanConstructor<B> generated;
        private final BeanConstructor<B> reflected;

        DescribedBeanConstructor(BeanConstructor<B> generated, BeanConstructor<B> reflected) {
            this.generated = generated;
            this.reflected = reflected;
        }

        @Override
        public Class<B> getDeclaringBeanType() {
            return generated.getDeclaringBeanType();
        }

        @Override
        public Argument<?>[] getArguments() {
            return ReflectionBeanIntrospection.mergeArguments(generated.getArguments(), reflected.getArguments());
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return reflected.getAnnotationMetadata();
        }

        @Override
        public B instantiate(@Nullable Object... parameterValues) {
            return generated.instantiate(parameterValues);
        }
    }

    /**
     * A generated property with the type arguments the reflection reads.
     *
     * @param <B> The bean type
     * @param <P> The property type
     */
    private static final class DescribedBeanProperty<B, P> implements BeanProperty<B, P> {

        private final BeanProperty<B, P> generated;
        private final Argument<P> argument;

        @SuppressWarnings("unchecked")
        DescribedBeanProperty(BeanProperty<B, P> generated, Argument<?> reflectedArgument) {
            this.generated = generated;
            this.argument = (Argument<P>) ReflectionBeanIntrospection.mergeTypeArguments(generated.asArgument(), reflectedArgument);
        }

        @Override
        public BeanIntrospection<B> getDeclaringBean() {
            return generated.getDeclaringBean();
        }

        @Override
        @SuppressWarnings("NullAway") // a property value can be null
        public P get(B bean) {
            return generated.get(bean);
        }

        @Override
        public B withValue(B bean, @Nullable P value) {
            return generated.withValue(bean, value);
        }

        @Override
        public void set(B bean, @Nullable P value) {
            generated.set(bean, value);
        }

        @Override
        public Class<P> getType() {
            return generated.getType();
        }

        @Override
        public Argument<P> asArgument() {
            return argument;
        }

        @Override
        public String getName() {
            return generated.getName();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return generated.getAnnotationMetadata();
        }

        @Override
        public boolean isReadOnly() {
            return generated.isReadOnly();
        }

        @Override
        public boolean isWriteOnly() {
            return generated.isWriteOnly();
        }

        @Override
        public boolean hasSetterOrConstructorArgument() {
            return generated.hasSetterOrConstructorArgument();
        }
    }

    /**
     * A generated bean method reporting the class the reflection knows declares it.
     *
     * @param <B> The bean type
     * @param <R> The return type
     */
    private static final class DeclaredBeanMethod<B, R> implements BeanMethod<B, R> {

        private final BeanMethod<B, R> delegate;
        private final Class<B> declaringType;

        DeclaredBeanMethod(BeanMethod<B, R> delegate, Class<B> declaringType) {
            this.delegate = delegate;
            this.declaringType = declaringType;
        }

        @Override
        public BeanIntrospection<B> getDeclaringBean() {
            return delegate.getDeclaringBean();
        }

        @Override
        public Class<B> getDeclaringType() {
            return declaringType;
        }

        @Override
        public io.micronaut.core.type.ReturnType<R> getReturnType() {
            return delegate.getReturnType();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return delegate.getAnnotationMetadata();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Argument<?>[] getArguments() {
            return delegate.getArguments();
        }

        @Override
        @SuppressWarnings("NullAway") // a method can return null
        public R invoke(B instance, @Nullable Object... arguments) {
            return delegate.invoke(instance, arguments);
        }
    }

    @Nullable
    private static <T> BeanMethod<T, Object> findMethod(List<BeanMethod<T, Object>> methods, String name, Class<?>[] parameterTypes) {
        for (BeanMethod<T, Object> method : methods) {
            if (method.getName().equals(name) && Arrays.equals(Argument.toClassArray(method.getArguments()), parameterTypes)) {
                return method;
            }
        }
        return null;
    }
}
