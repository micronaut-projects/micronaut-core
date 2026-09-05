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
package io.micronaut.reflection;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanPropertyMember;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * @since 5.2.0
 */
@Experimental
public final class SupplementedBeanIntrospection<T> implements ReflectiveIntrospection<T> {

    private final BeanIntrospection<T> generated;
    private final ReflectionBeanIntrospection<T> reflected;
    private final List<BeanMethod<T, Object>> methods;
    private final List<BeanProperty<T, Object>> properties;
    private final Map<String, BeanProperty<T, Object>> propertiesByName;
    private final List<BeanReadProperty<T, Object>> readProperties;
    private final List<BeanWriteProperty<T, Object>> writeProperties;

    /**
     * Creates an introspection completing a generated one with a reflective one.
     *
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
        // every view is built once: a caller reading a bean asks for a property per value it reads, and the
        // property it is given is the same instance every time
        Map<String, BeanProperty<T, Object>> reflectedProperties = new HashMap<>();
        for (BeanProperty<T, Object> property : reflected.getBeanProperties()) {
            reflectedProperties.putIfAbsent(property.getName(), property);
        }
        Collection<BeanProperty<T, Object>> generatedProperties = generated.getBeanProperties();
        List<BeanProperty<T, Object>> mergedProperties = new ArrayList<>(generatedProperties.size());
        Map<String, BeanProperty<T, Object>> byName = new LinkedHashMap<>(generatedProperties.size());
        for (BeanProperty<T, Object> property : generatedProperties) {
            BeanProperty<T, Object> reflectedProperty = reflectedProperties.get(property.getName());
            BeanProperty<T, Object> describedProperty = reflectedProperty == null
                ? property
                : new DescribedBeanProperty<>(property, reflectedProperty);
            mergedProperties.add(describedProperty);
            byName.putIfAbsent(describedProperty.getName(), describedProperty);
        }
        this.properties = Collections.unmodifiableList(mergedProperties);
        this.propertiesByName = Collections.unmodifiableMap(byName);
        // the read and the write views are the ones the generated introspection reports, not the merged view
        // filtered: a generated introspection describes a read property and a write property of its own, each
        // carrying the argument of the member behind it - a List<String> getTags() paired with a
        // setTags(Collection<String>) - which the one property merged from both cannot carry
        // the properties of a supplemented introspection are the ones the generated introspection describes,
        // so the read and the write views are its own: never the reflected members standing in for a view the
        // processor left empty, and never a property only reflection knows, which the merged view has not either
        this.readProperties = List.copyOf(generated.getBeanReadProperties());
        this.writeProperties = List.copyOf(generated.getBeanWriteProperties());
    }

    /**
     * The generated introspection this one completes.
     *
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
        return properties;
    }

    /**
     * The read properties the generated introspection reports, each as it reports it, completed by the ones
     * of a property it does not describe at all.
     *
     * <p>A generated introspection that legitimately reads nothing - a type written to and never read from -
     * reads nothing here either: the reflective members of the type do not stand in for the properties it
     * has none of.</p>
     *
     * @return The read properties
     */
    @Override
    public List<BeanReadProperty<T, Object>> getBeanReadProperties() {
        return readProperties;
    }

    /**
     * The write properties the generated introspection reports, each as it reports it, completed by the ones
     * of a property it does not describe at all, the way {@link #getBeanReadProperties()} completes the read
     * ones.
     *
     * @return The write properties
     */
    @Override
    public List<BeanWriteProperty<T, Object>> getBeanWriteProperties() {
        return writeProperties;
    }

    @Override
    public Optional<BeanProperty<T, Object>> getProperty(String name) {
        return Optional.ofNullable(propertiesByName.get(name));
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

    @Nullable
    private static <T> BeanMethod<T, Object> findMethod(List<BeanMethod<T, Object>> methods, String name, Class<?>[] parameterTypes) {
        for (BeanMethod<T, Object> method : methods) {
            if (method.getName().equals(name) && Arrays.equals(Argument.toClassArray(method.getArguments()), parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    /**
     * A generated constructor with the metadata of the reflective one.
     *
     * @param <B> The bean type
     */
    @Internal
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
    @Internal
    private static final class DescribedBeanProperty<B, P> implements BeanProperty<B, P> {

        private final BeanProperty<B, P> generated;
        private final BeanProperty<B, ?> reflected;
        private final Argument<P> argument;

        @SuppressWarnings("unchecked")
        DescribedBeanProperty(BeanProperty<B, P> generated, BeanProperty<B, ?> reflected) {
            this.generated = generated;
            this.reflected = reflected;
            this.argument = (Argument<P>) ReflectionBeanIntrospection.mergeTypeArguments(generated.asArgument(), reflected.asArgument());
        }

        /**
         * The members the generated introspection carries, and the ones reflection reads when it carries
         * none: a generated introspection describes them only when {@link io.micronaut.core.annotation.Introspected#members()}
         * asked for them, and the reflective description of the same property has them either way.
         *
         * @return The members of the property
         */
        @Override
        @SuppressWarnings("unchecked")
        public List<BeanPropertyMember<B, ?>> getMembers() {
            List<BeanPropertyMember<B, ?>> members = generated.getMembers();
            return members.isEmpty() ? (List<BeanPropertyMember<B, ?>>) (List<?>) reflected.getMembers() : members;
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
    @Internal
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
}
