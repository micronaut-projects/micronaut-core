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

import io.micronaut.context.AnnotationReflectionUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.AbstractBeanConstructor;
import io.micronaut.core.beans.AbstractBeanMethod;
import io.micronaut.core.beans.AbstractBeanProperty;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.annotation.ReflectionAnnotationMetadataBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link BeanIntrospection} over a {@link Class}, with the properties, the constructor and the methods a
 * generated introspection would have.
 *
 * <p>A property is a field, a getter or a setter of the type or of its super classes, merged by name: it is
 * read through the getter when there is one and through the field otherwise, written through the setter when
 * there is one and through a non-final field otherwise, and its metadata holds the annotations of all three
 * together with the type-use annotations of the property type. A bean method is a public instance method of
 * the type or of its super classes, {@link Object} excluded. The constructor is the one annotated
 * {@link Creator}, else the only public one, else the public one with no parameter, else the declared one
 * with the most parameters.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class ReflectionBeanIntrospection<T> implements ReflectiveIntrospection<T> {

    private final Class<T> beanType;
    private final AnnotationMetadata annotationMetadata;
    private final @Nullable Constructor<T> constructor;
    private final Argument<?>[] constructorArguments;
    private final BeanConstructor<T> beanConstructor;
    private final Map<String, PropertyMembers> propertyMembers = new LinkedHashMap<>();
    private final List<BeanProperty<T, Object>> properties;
    private final List<BeanMethod<T, Object>> methods;

    private ReflectionBeanIntrospection(Class<T> beanType) {
        this.beanType = beanType;
        this.annotationMetadata = ReflectionAnnotationMetadataBuilder.build(beanType);
        this.constructor = selectConstructor(beanType);
        this.constructorArguments = constructor == null ? Argument.ZERO_ARGUMENTS : AnnotationReflectionUtils.argumentsOf(constructor);
        this.beanConstructor = new ReflectionBeanConstructor();
        this.properties = Collections.unmodifiableList(discoverProperties());
        this.methods = Collections.unmodifiableList(discoverMethods());
    }

    /**
     * Introspects a type.
     *
     * @param beanType The type
     * @param <T>      The type
     * @return The introspection
     * @throws IllegalArgumentException When the type cannot be introspected, see {@link #isIntrospectable(Class)}
     */
    public static <T> ReflectionBeanIntrospection<T> of(Class<T> beanType) {
        if (!isIntrospectable(beanType)) {
            throw new IllegalArgumentException("The type " + beanType.getName() + " cannot be introspected reflectively");
        }
        return new ReflectionBeanIntrospection<>(beanType);
    }

    /**
     * @param type The type
     * @return Whether the type is a class a reflective introspection can describe: not a primitive, an array,
     * an interface, an annotation, an enum or a type of the JDK
     */
    public static boolean isIntrospectable(Class<?> type) {
        // an interface is introspected for its declarations, it cannot be instantiated
        return !type.isPrimitive()
            && !type.isArray()
            && !type.isAnnotation()
            && !type.isEnum()
            && !type.getName().startsWith("java.")
            && !type.getName().startsWith("javax.")
            && !type.getName().startsWith("jdk.");
    }

    @Override
    public Class<T> getBeanType() {
        return beanType;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Collection<BeanProperty<T, Object>> getBeanProperties() {
        return properties;
    }

    @Override
    public Collection<BeanMethod<T, Object>> getBeanMethods() {
        return methods;
    }

    @Override
    public Optional<BeanMethod<T, Object>> findDeclaredMethod(String name, Class<?>... parameterTypes) {
        for (BeanMethod<T, Object> method : methods) {
            if (method.getDeclaringType() == beanType
                && method.getName().equals(name)
                && Arrays.equals(Argument.toClassArray(method.getArguments()), parameterTypes)) {
                return Optional.of(method);
            }
        }
        // the accessors are properties, not bean methods, yet they are declarations of the type all the same
        try {
            Method declared = beanType.getDeclaredMethod(name, parameterTypes);
            if (Modifier.isStatic(declared.getModifiers()) || declared.isSynthetic()) {
                return Optional.empty();
            }
            declared.trySetAccessible();
            return Optional.of(new ReflectionMethod<>(this, new ReflectionExecutableMethod<>(beanType, declared)));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    @Override
    public Argument<?>[] getConstructorArguments() {
        return constructorArguments;
    }

    @Override
    public BeanConstructor<T> getConstructor() {
        return beanConstructor;
    }

    /**
     * Every constructor of the type, the selected one first. An introspection describes one constructor;
     * a specification that names constructors by their parameter types needs the others too.
     *
     * @return The constructors
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<BeanConstructor<T>> getConstructors() {
        List<BeanConstructor<T>> constructors = new ArrayList<>();
        constructors.add(beanConstructor);
        for (Constructor<?> declared : beanType.getDeclaredConstructors()) {
            if (declared.isSynthetic() || declared.equals(constructor)) {
                continue;
            }
            declared.trySetAccessible();
            constructors.add(new ReflectionBeanConstructor((Constructor<T>) declared));
        }
        return Collections.unmodifiableList(constructors);
    }

    @Override
    public Collection<BeanProperty<T, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) {
        List<BeanProperty<T, Object>> indexed = new ArrayList<>(2);
        for (BeanProperty<T, Object> property : properties) {
            if (property.getAnnotationMetadata().hasStereotype(annotationType)) {
                indexed.add(property);
            }
        }
        return indexed;
    }

    @Override
    public Optional<BeanProperty<T, Object>> getIndexedProperty(Class<? extends Annotation> annotationType, String annotationValue) {
        for (BeanProperty<T, Object> property : properties) {
            if (annotationValue.equals(property.getAnnotationMetadata().stringValue(annotationType).orElse(null))) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }

    @Override
    public T instantiate() throws InstantiationException {
        return instantiate(true, Argument.ZERO_ARGUMENTS);
    }

    @Override
    public T instantiate(boolean strictNullable, @Nullable Object... arguments) throws InstantiationException {
        if (constructor == null) {
            throw new InstantiationException("The type " + beanType.getName() + " declares no constructor a reflective introspection can invoke");
        }
        Object[] values = arguments == null ? new Object[0] : arguments;
        if (values.length != constructorArguments.length) {
            throw new InstantiationException("The constructor of " + beanType.getName() + " expects "
                + constructorArguments.length + " argument(s), " + values.length + " given");
        }
        if (strictNullable) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null && !constructorArguments[i].isNullable()) {
                    throw new InstantiationException("Null argument specified for [" + constructorArguments[i].getName()
                        + "] of " + beanType.getName());
                }
            }
        }
        try {
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBuildable() {
        return constructor != null;
    }

    @Override
    public Builder<T> builder() {
        return new ReflectionBuilder();
    }

    @Override
    public String toString() {
        return "ReflectionBeanIntrospection(" + beanType.getName() + ")";
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable Constructor<T> selectConstructor(Class<T> beanType) {
        Constructor<?>[] declared = beanType.getDeclaredConstructors();
        Constructor<?> selected = null;
        for (Constructor<?> candidate : declared) {
            if (candidate.isAnnotationPresent(Creator.class)) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            List<Constructor<?>> visible = new ArrayList<>(declared.length);
            for (Constructor<?> candidate : declared) {
                if (Modifier.isPublic(candidate.getModifiers()) && !candidate.isSynthetic()) {
                    visible.add(candidate);
                }
            }
            if (visible.size() == 1) {
                selected = visible.get(0);
            } else {
                for (Constructor<?> candidate : visible) {
                    if (candidate.getParameterCount() == 0) {
                        selected = candidate;
                        break;
                    }
                }
            }
        }
        if (selected == null) {
            for (Constructor<?> candidate : declared) {
                if (!candidate.isSynthetic() && (selected == null || candidate.getParameterCount() > selected.getParameterCount())) {
                    selected = candidate;
                }
            }
        }
        if (selected != null) {
            selected.trySetAccessible();
        }
        return (Constructor<T>) selected;
    }

    @Override
    public List<PropertyMember> getPropertyMembers(String propertyName) {
        PropertyMembers members = propertyMembers.get(propertyName);
        return members == null ? List.of() : members.members();
    }

    private List<BeanProperty<T, Object>> discoverProperties() {
        Map<String, PropertyMembers> members = propertyMembers;
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                // an accessor of any visibility is a member of the property: what it declares holds
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                String name = method.getName();
                int parameters = method.getParameterCount();
                if (parameters == 0 && method.getReturnType() != void.class) {
                    String property = accessorProperty(name, method.getReturnType());
                    if (property != null) {
                        members.computeIfAbsent(property, PropertyMembers::new).addGetter(method);
                    }
                } else if (parameters == 1 && name.startsWith("set") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                    String property = decapitalize(name.substring(3));
                    members.computeIfAbsent(property, PropertyMembers::new).addSetter(method);
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                members.computeIfAbsent(field.getName(), PropertyMembers::new).addField(field);
            }
        }
        // the getters of the interfaces, which declare the type-use annotations of the implementations' properties
        for (Class<?> anInterface : allInterfaces(beanType)) {
            for (Method method : anInterface.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.getParameterCount() != 0
                    || method.getReturnType() == void.class) {
                    continue;
                }
                String property = accessorProperty(method.getName(), method.getReturnType());
                if (property != null) {
                    members.computeIfAbsent(property, PropertyMembers::new).addGetter(method);
                }
            }
        }
        List<BeanProperty<T, Object>> discovered = new ArrayList<>(members.size());
        for (PropertyMembers property : members.values()) {
            if (property.getter() == null && property.field() == null && property.setter() == null) {
                continue;
            }
            discovered.add(new ReflectionProperty<>(this, property));
        }
        return discovered;
    }

    @Nullable
    private static String accessorProperty(String name, Class<?> returnType) {
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
            && (returnType == boolean.class || returnType == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static List<Class<?>> allInterfaces(Class<?> type) {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            collectInterfaces(current, interfaces);
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> type, List<Class<?>> interfaces) {
        for (Class<?> anInterface : type.getInterfaces()) {
            if (!interfaces.contains(anInterface)) {
                interfaces.add(anInterface);
                collectInterfaces(anInterface, interfaces);
            }
        }
    }

    /**
     * Merges the metadata of the type arguments of two arguments of the same type, the first argument
     * winning the type and the names.
     *
     * @param first  The first argument
     * @param second The second argument
     * @return The merged argument
     */
    static Argument<?> mergeTypeArguments(Argument<?> first, Argument<?> second) {
        if (first.getType() != second.getType()) {
            return first;
        }
        Argument<?>[] firstParameters = first.getTypeParameters();
        Argument<?>[] secondParameters = second.getTypeParameters();
        if (firstParameters.length == 0 || firstParameters.length != secondParameters.length) {
            return mergeMetadata(first, second.getAnnotationMetadata(), firstParameters);
        }
        Argument<?>[] merged = new Argument[firstParameters.length];
        for (int i = 0; i < merged.length; i++) {
            merged[i] = mergeTypeArguments(firstParameters[i], secondParameters[i]);
        }
        return mergeMetadata(first, second.getAnnotationMetadata(), merged);
    }

    /**
     * Merges two argument arrays of the same arity pairwise.
     *
     * @param first  The first arguments
     * @param second The second arguments
     * @return The merged arguments, the first ones when the arities differ
     */
    static Argument<?>[] mergeArguments(Argument<?>[] first, Argument<?>[] second) {
        if (first.length != second.length) {
            return first;
        }
        Argument<?>[] merged = new Argument[first.length];
        for (int i = 0; i < merged.length; i++) {
            merged[i] = mergeTypeArguments(first[i], second[i]);
        }
        return merged;
    }

    /**
     * The metadata of the first argument wins when it has any: the second completes a type argument the
     * first left bare, it does not repeat the annotations the first already carries.
     */
    private static Argument<?> mergeMetadata(Argument<?> argument, AnnotationMetadata additional, Argument<?>[] typeParameters) {
        AnnotationMetadata metadata = argument.getAnnotationMetadata();
        if (metadata.isEmpty() && !additional.isEmpty()) {
            metadata = additional;
        }
        return Argument.of(argument.getType(), argument.getName(), metadata, typeParameters);
    }

    private static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private List<BeanMethod<T, Object>> discoverMethods() {
        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                bySignature.putIfAbsent(signature(method), method);
            }
        }
        List<BeanMethod<T, Object>> discovered = new ArrayList<>(bySignature.size());
        for (Method method : bySignature.values()) {
            discovered.add(new ReflectionMethod<>(this, new ReflectionExecutableMethod<>(beanType, method)));
        }
        return discovered;
    }

    private static String signature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            signature.append(parameterType.getName()).append(',');
        }
        return signature.append(')').toString();
    }

    /**
     * The members a property is made of, the most specific first: a field can be shadowed in a sub class and
     * a getter overridden, and the constraints of every declaration apply.
     */
    private static final class PropertyMembers {

        private final String name;
        private final List<Field> fields = new ArrayList<>(1);
        private final List<Method> getters = new ArrayList<>(1);
        private final List<Method> setters = new ArrayList<>(1);

        PropertyMembers(String name) {
            this.name = name;
        }

        void addField(Field field) {
            fields.add(field);
        }

        void addGetter(Method getter) {
            getters.add(getter);
        }

        void addSetter(Method setter) {
            setters.add(setter);
        }

        @Nullable Field field() {
            return fields.isEmpty() ? null : fields.get(0);
        }

        @Nullable Method getter() {
            return getters.isEmpty() ? null : getters.get(0);
        }

        @Nullable Method setter() {
            return setters.isEmpty() ? null : setters.get(0);
        }

        /**
         * The argument of the property: its type and type arguments with their type-use annotations. Every
         * declaration can annotate the type arguments — {@code List<@Size String>} on the field, on the
         * return type of the getter of an interface — so the arguments of all of them are merged.
         */
        Argument<?> argument() {
            Argument<?> merged = null;
            for (AnnotatedType annotatedType : annotatedTypes()) {
                Argument<?> argument = AnnotationReflectionUtils.argumentOf(name, annotatedType);
                merged = merged == null ? argument : mergeTypeArguments(merged, argument);
            }
            if (merged == null) {
                throw new IllegalStateException("The property '" + name + "' has no member");
            }
            return merged;
        }

        private List<AnnotatedType> annotatedTypes() {
            List<AnnotatedType> types = new ArrayList<>(3);
            for (Field field : fields) {
                types.add(field.getAnnotatedType());
            }
            for (Method getter : getters) {
                types.add(getter.getAnnotatedReturnType());
            }
            for (Method setter : setters) {
                types.add(setter.getAnnotatedParameterTypes()[0]);
            }
            return types;
        }

        /**
         * The members with their own metadata, the most specific first.
         */
        List<PropertyMember> members() {
            List<PropertyMember> members = new ArrayList<>(fields.size() + getters.size() + setters.size());
            for (Field field : fields) {
                members.add(new PropertyMember(ElementType.FIELD, field.getDeclaringClass(),
                    withType(ReflectionAnnotationMetadataBuilder.build(field), field.getAnnotatedType()),
                    AnnotationReflectionUtils.argumentOf(name, field.getAnnotatedType()),
                    field));
            }
            for (Method getter : getters) {
                members.add(new PropertyMember(ElementType.METHOD, getter.getDeclaringClass(),
                    withType(ReflectionAnnotationMetadataBuilder.build(getter), getter.getAnnotatedReturnType()),
                    AnnotationReflectionUtils.argumentOf(name, getter.getAnnotatedReturnType()),
                    getter));
            }
            for (Method setter : setters) {
                members.add(new PropertyMember(ElementType.METHOD, setter.getDeclaringClass(),
                    ReflectionAnnotationMetadataBuilder.build(setter, setter.getParameters()[0]),
                    AnnotationReflectionUtils.argumentOf(name, setter.getAnnotatedParameterTypes()[0]),
                    setter));
            }
            return members;
        }

        private static AnnotationMetadata withType(AnnotationMetadata member, AnnotatedType type) {
            AnnotationMetadata typeMetadata = ReflectionAnnotationMetadataBuilder.build(type);
            if (typeMetadata.isEmpty()) {
                return member;
            }
            return member.isEmpty() ? typeMetadata : new AnnotationMetadataHierarchy(true, member, typeMetadata);
        }

        private static Argument<?> mergeTypeArguments(Argument<?> first, Argument<?> second) {
            return ReflectionBeanIntrospection.mergeTypeArguments(first, second);
        }

        /**
         * The metadata of the property: every member, the most specific first, and the type-use annotations
         * of its type.
         */
        AnnotationMetadata annotationMetadata(Argument<?> typed) {
            MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
            for (Field field : fields) {
                ReflectionAnnotationMetadataBuilder.add(metadata, field);
            }
            for (Method getter : getters) {
                ReflectionAnnotationMetadataBuilder.add(metadata, getter);
            }
            for (Method setter : setters) {
                ReflectionAnnotationMetadataBuilder.add(metadata, setter);
                ReflectionAnnotationMetadataBuilder.add(metadata, setter.getParameters()[0]);
            }
            AnnotationMetadata typeMetadata = typed.getAnnotationMetadata();
            if (typeMetadata.isEmpty()) {
                return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
            }
            return metadata.isEmpty() ? typeMetadata : new AnnotationMetadataHierarchy(true, metadata, typeMetadata);
        }
    }

    /**
     * A constructor with its annotation metadata: a constraint or a cascade declared on the constructor
     * itself is read from here.
     */
    private final class ReflectionBeanConstructor extends AbstractBeanConstructor<T> {

        private final @Nullable Constructor<T> target;

        ReflectionBeanConstructor() {
            super(beanType,
                constructor == null ? AnnotationMetadata.EMPTY_METADATA : ReflectionAnnotationMetadataBuilder.build(constructor),
                constructorArguments);
            this.target = constructor;
        }

        ReflectionBeanConstructor(Constructor<T> target) {
            super(beanType, ReflectionAnnotationMetadataBuilder.build(target), AnnotationReflectionUtils.argumentsOf(target));
            this.target = target;
        }

        @Override
        public T instantiate(@Nullable Object... parameterValues) {
            if (target == null || target == constructor) {
                return ReflectionBeanIntrospection.this.instantiate(true, parameterValues);
            }
            try {
                return target.newInstance(parameterValues == null ? new Object[0] : parameterValues);
            } catch (InvocationTargetException e) {
                throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getTargetException().getMessage(), e.getTargetException());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * A property read and written through its members.
     *
     * @param <B> The bean type
     * @param <P> The property type
     */
    private static final class ReflectionProperty<B, P> extends AbstractBeanProperty<B, P> {

        private final @Nullable Field field;
        private final @Nullable Method getter;
        private final @Nullable Method setter;

        @SuppressWarnings("unchecked")
        ReflectionProperty(BeanIntrospection<B> introspection, PropertyMembers members) {
            this(introspection, members, members.argument());
        }

        @SuppressWarnings("unchecked")
        private ReflectionProperty(BeanIntrospection<B> introspection, PropertyMembers members, Argument<?> typed) {
            super(introspection, (Class<P>) typed.getType(), members.name, members.annotationMetadata(typed), typed.getTypeParameters());
            this.field = members.field();
            this.getter = members.getter();
            this.setter = members.setter();
            if (field != null) {
                field.trySetAccessible();
            }
        }

        @Override
        public boolean isReadOnly() {
            return setter == null && (field == null || Modifier.isFinal(field.getModifiers()));
        }

        @Override
        public boolean isWriteOnly() {
            return getter == null && field == null;
        }

        @Override
        @SuppressWarnings({"unchecked", "NullAway"}) // a property value can be null, the declaration of readInternal predates the nullness annotations
        protected P readInternal(B bean) {
            if (getter != null) {
                return ReflectionUtils.invokeMethod(bean, getter);
            }
            if (field == null) {
                throw new UnsupportedOperationException("The property '" + getName() + "' of " + getDeclaringType().getName() + " is write only");
            }
            try {
                return (P) field.get(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read the field '" + getName() + "' of " + getDeclaringType().getName(), e);
            }
        }

        @Override
        protected void writeInternal(B bean, @Nullable P value) {
            if (setter != null) {
                ReflectionUtils.invokeMethod(bean, setter, value);
                return;
            }
            if (field == null || Modifier.isFinal(field.getModifiers())) {
                throw new UnsupportedOperationException("The property '" + getName() + "' of " + getDeclaringType().getName() + " is read only");
            }
            try {
                field.set(bean, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot write the field '" + getName() + "' of " + getDeclaringType().getName(), e);
            }
        }
    }

    /**
     * A bean method dispatching to a {@link ReflectionExecutableMethod}.
     *
     * @param <B> The bean type
     * @param <R> The return type
     */
    private static final class ReflectionMethod<B, R> extends AbstractBeanMethod<B, R> {

        private final ReflectionExecutableMethod<B, R> executable;

        ReflectionMethod(BeanIntrospection<B> introspection, ReflectionExecutableMethod<B, R> executable) {
            super(introspection,
                executable.getReturnType().asArgument(),
                executable.getMethodName(),
                executable.getAnnotationMetadata(),
                executable.getArguments());
            this.executable = executable;
        }

        /**
         * The class declaring the method, which is a super class for an inherited method: the bean type is
         * {@link #getDeclaringBean()}.
         */
        @Override
        @SuppressWarnings("unchecked")
        public Class<B> getDeclaringType() {
            return (Class<B>) executable.getMethod().getDeclaringClass();
        }

        /**
         * @return The method
         */
        public Method getMethod() {
            return executable.getMethod();
        }

        @Override
        @SuppressWarnings("NullAway") // a method can return null, the declaration of invokeInternal predates the nullness annotations
        protected R invokeInternal(B instance, @Nullable Object... arguments) {
            return executable.invoke(instance, arguments);
        }
    }

    /**
     * A builder collecting the constructor arguments by name or index.
     */
    private final class ReflectionBuilder implements Builder<T> {

        private final Object[] values = new Object[constructorArguments.length];

        @Override
        public Argument<?>[] getBuilderArguments() {
            return constructorArguments;
        }

        @Override
        public Argument<?>[] getBuildMethodArguments() {
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public int indexOf(String name) {
            for (int i = 0; i < constructorArguments.length; i++) {
                if (constructorArguments[i].getName().equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Builder<T> with(String name, @Nullable Object value) {
            int index = indexOf(name);
            if (index == -1) {
                throw new IllegalArgumentException("No constructor argument named '" + name + "' in " + beanType.getName());
            }
            values[index] = value;
            return this;
        }

        @Override
        public Builder<T> with(T existing) {
            for (BeanProperty<T, Object> property : properties) {
                int index = indexOf(property.getName());
                if (index != -1 && !property.isWriteOnly()) {
                    values[index] = property.get(existing);
                }
            }
            return this;
        }

        @Override
        public <A> Builder<T> with(int index, Argument<A> argument, @Nullable A value) {
            values[index] = value;
            return this;
        }

        @Override
        public <A> Builder<T> convert(int index, ArgumentConversionContext<A> argument, @Nullable Object value, ConversionService conversionService) {
            values[index] = conversionService.convertRequired(value, argument);
            return this;
        }

        @Override
        public T build() {
            return instantiate(false, values);
        }

        @Override
        public T build(Object... buildMethodArguments) {
            return build();
        }
    }
}
