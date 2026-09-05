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

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.AbstractBeanMethod;
import io.micronaut.core.beans.AbstractBeanProperty;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanPropertyMember;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link BeanIntrospection} over a {@link Class}, with the properties, the constructor and the methods a
 * generated introspection would have.
 *
 * <p>A property is a getter or a setter of the type or of its super classes - named by the prefixes
 * {@link io.micronaut.core.annotation.AccessorsStyle} declares, {@code get}, {@code is} and {@code set} by
 * default - merged by name, with the field of that name as a member of it: it is read through the getter when
 * there is one and through the field otherwise, written through the setter when there is one and through a
 * non-final field otherwise, and its metadata holds the annotations of all three together with the type-use
 * annotations of the property type. Only an accessor the visibility of the type admits - a non-private one by
 * default - makes a property, and a field alone makes one only when field access is asked for -
 * {@link Introspected#accessKind()} on the type, or the access kinds given to {@link #of(Class, Set)} - which
 * is what the processors admit; with field access alone the value goes through the field even where an
 * accessor exists. The accessor of a record component always makes a property, and so does a member
 * annotated {@link Introspected.Property}, under the name of the method when the method carries no prefix,
 * read and written as its access kinds allow. A bean method is a public instance method of the type, of its
 * super classes or of its interfaces, {@link Object} excluded. The constructor is the static factory
 * annotated {@link Creator} when the type declares one, else the constructor annotated {@link Creator}, else
 * the only public one, else the public one with no parameter, else the declared one with the most
 * parameters; a type naming a builder in {@link Introspected#builder()} is built through that builder.</p>
 *
 * <p>The type of a property, of a method argument and of a return type is the type the bean type sees:
 * {@code T} declared by a {@code Box<T>} and read through a {@code StringBox extends Box<String>} is
 * {@code String}, not the bound of the variable.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionBeanIntrospection<T> implements ReflectiveIntrospection<T> {

    private static final Set<Introspected.AccessKind> METHOD_ACCESS = Set.of(Introspected.AccessKind.METHOD);

    private final Class<T> beanType;
    private final AnnotationMetadata annotationMetadata;
    private final Set<Introspected.AccessKind> accessKinds;
    private final Set<Introspected.Visibility> visibility;
    private final @Nullable Constructor<T> constructor;
    private final @Nullable Method factory;
    private final Argument<?>[] constructorArguments;
    private final String[] readPrefixes;
    private final String[] writePrefixes;
    private final ReflectionIntrospectionBuilder.@Nullable Support<T> builderSupport;
    private final BeanConstructor<T> beanConstructor;
    private final List<BeanProperty<T, Object>> properties;
    private final Map<String, BeanProperty<T, Object>> propertiesByName;
    private final List<BeanMethod<T, Object>> methods;
    private final Map<String, String> indexedAnnotations;
    private final Set<String> includes;
    private final Set<String> excludes;
    private final Set<String> excludedAnnotations;
    private final boolean describeAnnotations;

    private ReflectionBeanIntrospection(Class<T> beanType,
                                        AnnotationMetadata additionalAnnotationMetadata,
                                        @Nullable Set<Introspected.AccessKind> requestedAccessKinds) {
        this.beanType = beanType;
        // the annotations the caller means the type to carry win where both declare the same one: a
        // specification told to handle a type says what that type is, over what the class says of itself
        this.annotationMetadata = ReflectionAnnotations.merge(additionalAnnotationMetadata, ReflectionAnnotations.metadataOf(beanType));
        if (requestedAccessKinds == null) {
            // the type was annotated: the introspection describes it the way the processor would have
            Set<Introspected.AccessKind> declared = annotationMetadata.enumValuesSet(Introspected.class, "accessKind", Introspected.AccessKind.class);
            Set<Introspected.Visibility> declaredVisibility = annotationMetadata.enumValuesSet(Introspected.class, "visibility", Introspected.Visibility.class);
            this.accessKinds = declared.isEmpty() ? METHOD_ACCESS : Set.copyOf(declared);
            this.visibility = declaredVisibility.isEmpty() ? Set.of(Introspected.Visibility.DEFAULT) : Set.copyOf(declaredVisibility);
        } else {
            // the caller asked for the kinds itself, and reflection reaches a member of any visibility
            this.accessKinds = requestedAccessKinds.isEmpty() ? METHOD_ACCESS : Set.copyOf(requestedAccessKinds);
            this.visibility = Set.of(Introspected.Visibility.ANY);
        }
        this.includes = names(annotationMetadata, "includes");
        this.excludes = names(annotationMetadata, "excludes");
        this.excludedAnnotations = names(annotationMetadata, "excludedAnnotations");
        this.describeAnnotations = annotationMetadata.booleanValue(Introspected.class, "annotationMetadata").orElse(true);
        this.readPrefixes = prefixes(annotationMetadata, "readPrefixes", AccessorsStyle.DEFAULT_READ_PREFIX);
        this.writePrefixes = prefixes(annotationMetadata, "writePrefixes", AccessorsStyle.DEFAULT_WRITE_PREFIX);
        // a static @Creator factory is the instantiation route the processors select first, before any constructor
        this.factory = selectFactoryMethod(beanType);
        this.constructor = factory == null ? selectConstructor(beanType) : null;
        this.constructorArguments = factory != null
            ? ReflectionArguments.argumentsOf(factory)
            : constructor == null ? Argument.ZERO_ARGUMENTS : ReflectionArguments.argumentsOf(constructor);
        this.beanConstructor = new SelectedBeanConstructor();
        this.properties = Collections.unmodifiableList(discoverProperties());
        Map<String, BeanProperty<T, Object>> byName = new LinkedHashMap<>(properties.size());
        for (BeanProperty<T, Object> property : properties) {
            byName.putIfAbsent(property.getName(), property);
        }
        this.propertiesByName = Collections.unmodifiableMap(byName);
        this.methods = Collections.unmodifiableList(discoverMethods());
        this.indexedAnnotations = indexedAnnotationsOf(annotationMetadata);
        this.builderSupport = ReflectionIntrospectionBuilder.Support.of(beanType, annotationMetadata);
    }

    /**
     * The prefixes {@link AccessorsStyle} declares for the accessors of the type, the default one when it
     * declares none: what makes a method a getter or a setter to the processors.
     */
    private static String[] prefixes(AnnotationMetadata metadata, String member, String defaultPrefix) {
        String[] declared = metadata.stringValues(AccessorsStyle.class, member);
        return declared.length == 0 ? new String[] {defaultPrefix} : declared;
    }

    /**
     * The annotations {@link Introspected#indexed()} names, each mapped to the member
     * {@link Introspected.IndexedAnnotation#member()} names, or the empty string when it names none: the index a
     * generated introspection carries is built for these annotations alone, and looking up any other one finds
     * nothing there.
     */
    private static Map<String, String> indexedAnnotationsOf(AnnotationMetadata metadata) {
        AnnotationValue<Introspected> introspected = metadata.getAnnotation(Introspected.class);
        if (introspected == null) {
            return Map.of();
        }
        List<AnnotationValue<Annotation>> declared = introspected.getAnnotations("indexed");
        if (declared.isEmpty()) {
            return Map.of();
        }
        Map<String, String> indexed = new LinkedHashMap<>(declared.size());
        for (AnnotationValue<Annotation> value : declared) {
            value.stringValue("annotation")
                .ifPresent(annotation -> indexed.put(annotation, value.stringValue("member").orElse("")));
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * The names a member of {@link Introspected} lists, empty when it lists none. A class value is read by
     * name, so that an annotation type the application does not have on its class path is still named.
     */
    private static Set<String> names(AnnotationMetadata metadata, String member) {
        String[] values = metadata.stringValues(Introspected.class, member);
        return values.length == 0 ? Set.of() : Set.of(values);
    }

    /**
     * Introspects a type through its accessors, which is what a generated introspection describes by default:
     * a field of the type is a member of the property of its name, it does not make one of its own unless
     * the type carries {@link Introspected#accessKind()} asking for field access.
     *
     * @param beanType The type
     * @param <T>      The type
     * @return The introspection
     * @throws IllegalArgumentException When the type cannot be introspected, see {@link #isIntrospectable(Class)}
     */
    public static <T> ReflectionBeanIntrospection<T> of(Class<T> beanType) {
        return of(beanType, AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * The reflective introspection of a type, carrying annotations the caller means it to have beyond the ones
     * the class declares.
     *
     * <p>A specification that reads a type it was told to handle - serialization asked to read a class that
     * was never annotated - decides that itself, and the code downstream asks the introspection for the
     * annotation rather than being told separately.</p>
     *
     * @param beanType                     The bean type
     * @param additionalAnnotationMetadata The annotations to carry beyond the ones of the class
     * @param <T>                          The bean type
     * @return The introspection, describing the type through its accessors unless the annotations ask for
     * field access
     * @throws IllegalArgumentException When the type cannot be described reflectively
     */
    public static <T> ReflectionBeanIntrospection<T> of(Class<T> beanType, AnnotationMetadata additionalAnnotationMetadata) {
        if (!isIntrospectable(beanType)) {
            throw new IllegalArgumentException("The type " + beanType.getName() + " cannot be introspected reflectively");
        }
        return new ReflectionBeanIntrospection<>(beanType, additionalAnnotationMetadata, null);
    }

    /**
     * The reflective introspection of a type described through the access kinds the caller asks for, rather
     * than through the ones {@link Introspected#accessKind()} declares.
     *
     * <p>A reflective introspection exists for the types the processors never saw, which carry no annotation
     * to read the kinds from: a caller that means the fields of such a type to be properties - a mapping
     * layer over a class of plain fields - says so here. The members of the kinds asked for are taken
     * whatever their visibility, a private field included: reflection reaches them, and asking for field
     * access is asking for exactly that.</p>
     *
     * @param beanType    The bean type
     * @param accessKinds The kinds of member that make a property, empty for
     *                    {@link Introspected.AccessKind#METHOD} alone
     * @param <T>         The bean type
     * @return The introspection
     * @throws IllegalArgumentException When the type cannot be described reflectively
     */
    public static <T> ReflectionBeanIntrospection<T> of(Class<T> beanType, Set<Introspected.AccessKind> accessKinds) {
        return of(beanType, AnnotationMetadata.EMPTY_METADATA, accessKinds);
    }

    /**
     * The reflective introspection of a type described through the access kinds the caller asks for, carrying
     * annotations the caller means it to have beyond the ones the class declares.
     *
     * @param beanType                     The bean type
     * @param additionalAnnotationMetadata The annotations to carry beyond the ones of the class
     * @param accessKinds                  The kinds of member that make a property, empty for
     *                                     {@link Introspected.AccessKind#METHOD} alone
     * @param <T>                          The bean type
     * @return The introspection
     * @throws IllegalArgumentException When the type cannot be described reflectively
     * @see #of(Class, Set)
     */
    public static <T> ReflectionBeanIntrospection<T> of(Class<T> beanType,
                                                        AnnotationMetadata additionalAnnotationMetadata,
                                                        Set<Introspected.AccessKind> accessKinds) {
        if (!isIntrospectable(beanType)) {
            throw new IllegalArgumentException("The type " + beanType.getName() + " cannot be introspected reflectively");
        }
        return new ReflectionBeanIntrospection<>(beanType, additionalAnnotationMetadata, accessKinds);
    }

    /**
     * Whether a type can be described reflectively.
     *
     * @param type The type
     * @return Whether the type is a class or an interface a reflective introspection can describe: not a
     * primitive, an array, an annotation, an enum or a type of the JDK
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

    /**
     * The property of a name, from the properties indexed by name rather than by walking them: a caller
     * reading a bean asks for a property per value it reads.
     *
     * @param name The property name
     * @return The property, empty when the type has none of that name
     */
    @Override
    public Optional<BeanProperty<T, Object>> getProperty(String name) {
        return Optional.ofNullable(propertiesByName.get(name));
    }

    /**
     * The properties a value can be read from: every property but a write only one. A generated introspection
     * keeps this view apart from {@link #getBeanProperties()}, and the code reading a bean - serialization
     * among it - asks for this one.
     *
     * @return The read properties
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<BeanReadProperty<T, Object>> getBeanReadProperties() {
        List<BeanReadProperty<T, Object>> readProperties = new ArrayList<>(properties.size());
        for (BeanProperty<T, Object> property : properties) {
            if (!property.isWriteOnly()) {
                readProperties.add((BeanReadProperty<T, Object>) property);
            }
        }
        return Collections.unmodifiableList(readProperties);
    }

    /**
     * The properties a value can be written to: every property but a read only one.
     *
     * @return The write properties
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<BeanWriteProperty<T, Object>> getBeanWriteProperties() {
        List<BeanWriteProperty<T, Object>> writeProperties = new ArrayList<>(properties.size());
        for (BeanProperty<T, Object> property : properties) {
            if (!property.isReadOnly()) {
                writeProperties.add((BeanWriteProperty<T, Object>) property);
            }
        }
        return Collections.unmodifiableList(writeProperties);
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
     * The constructor the introspection instantiates through, {@code null} when it instantiates through a
     * static factory or through none.
     */
    @Nullable Constructor<T> selectedConstructor() {
        return constructor;
    }

    /**
     * Every constructor of the type, the selected one first, the others by arity and then by parameter types.
     * An introspection describes one constructor; a specification that names constructors by their parameter
     * types needs the others too.
     *
     * @return The constructors
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<BeanConstructor<T>> getConstructors() {
        List<BeanConstructor<T>> constructors = new ArrayList<>();
        constructors.add(beanConstructor);
        // reflection reports the constructors in no particular order: the others are listed by arity, then by
        // parameter types, so that the list is the same on every JVM
        List<Constructor<?>> others = new ArrayList<>();
        for (Constructor<?> declared : beanType.getDeclaredConstructors()) {
            if (!declared.isSynthetic() && !declared.equals(constructor)) {
                others.add(declared);
            }
        }
        others.sort(Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount)
            .thenComparing(declared -> Arrays.toString(declared.getParameterTypes())));
        for (Constructor<?> declared : others) {
            declared.trySetAccessible();
            constructors.add(new ReflectionBeanConstructor<>((Constructor<T>) declared));
        }
        return Collections.unmodifiableList(constructors);
    }

    /**
     * The properties carrying an annotation, out of the index {@link Introspected#indexed()} asks for. The
     * processor builds an index for the annotations named there and for no other, so an annotation the type does
     * not index finds nothing here either, as it finds nothing in a generated introspection.
     */
    @Override
    public Collection<BeanProperty<T, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) {
        if (!isIndexed(annotationType)) {
            return Collections.emptyList();
        }
        List<BeanProperty<T, Object>> indexed = new ArrayList<>(2);
        for (BeanProperty<T, Object> property : properties) {
            if (property.getAnnotationMetadata().hasStereotype(annotationType)) {
                indexed.add(property);
            }
        }
        return Collections.unmodifiableList(indexed);
    }

    /**
     * The property whose annotation carries a value, out of the index {@link Introspected#indexed()} asks for.
     * The member read is the one {@link Introspected.IndexedAnnotation#member()} names: an entry naming none is
     * an index by annotation alone, which no value matches.
     */
    @Override
    public Optional<BeanProperty<T, Object>> getIndexedProperty(Class<? extends Annotation> annotationType, String annotationValue) {
        String member = isIndexed(annotationType) ? indexedAnnotations.get(annotationType.getName()) : null;
        if (member == null || member.isEmpty()) {
            return Optional.empty();
        }
        for (BeanProperty<T, Object> property : properties) {
            if (annotationValue.equals(property.getAnnotationMetadata().stringValue(annotationType, member).orElse(null))) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the index holds an annotation: the type asks for it in {@link Introspected#indexed()} and the
     * annotation is one the processor indexes.
     *
     * <p>A repeatable annotation is not one of them. The processor reads the index off the element, which carries
     * such an annotation under its container and not under its own name, so it indexes nothing for it however
     * many times the annotation is written; the metadata the same element yields does report it, under its own
     * name, once it is read back at runtime. Reporting the properties here that a generated introspection does
     * not report is the one difference a caller would see on switching, so the index is left empty for a
     * repeatable annotation as well.</p>
     */
    private boolean isIndexed(Class<? extends Annotation> annotationType) {
        return annotationType.getAnnotation(Repeatable.class) == null
            && indexedAnnotations.containsKey(annotationType.getName());
    }

    @Override
    public T instantiate() throws InstantiationException {
        return instantiate(true, new Object[0]);
    }

    @Override
    public T instantiate(boolean strictNullable, @Nullable Object... arguments) throws InstantiationException {
        Method factory = this.factory;
        Constructor<T> constructor = this.constructor;
        if (constructor == null && factory == null) {
            throw new InstantiationException("The type " + beanType.getName() + " declares no constructor a reflective introspection can invoke");
        }
        Object[] values = arguments == null ? new Object[0] : arguments; // NOSONAR - the annotation marks the elements nullable, a caller can still pass a null array
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
            if (factory != null) {
                return beanType.cast(factory.invoke(null, values));
            }
            return Objects.requireNonNull(constructor).newInstance(values);
        } catch (InvocationTargetException e) {
            throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new InstantiationException("Cannot instantiate " + beanType.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBuildable() {
        return constructor != null || factory != null || builderSupport != null;
    }

    /**
     * Whether {@link Introspected#builder()} configures a builder type, which {@link #builder()} then builds
     * through, as a generated introspection does.
     */
    @Override
    public boolean hasBuilder() {
        return builderSupport != null;
    }

    @Override
    public Builder<T> builder() {
        if (builderSupport != null) {
            return new ReflectionIntrospectionBuilder<>(this, builderSupport);
        }
        return new ReflectionBuilder();
    }

    @Override
    public String toString() {
        return "ReflectionBeanIntrospection(" + beanType.getName() + ")";
    }

    /**
     * The static factory method as the processors select it - the selection of
     * {@code ClassElement#findStaticCreator()}: an accessible static method the type itself declares,
     * annotated {@link Creator} through its metadata and returning the type or a sub type of it. When the
     * type declares several, the only one taking parameters, else the first public one.
     *
     * @param type The type
     * @return The factory method, {@code null} when the type declares none
     */
    static @Nullable Method selectFactoryMethod(Class<?> type) {
        List<Method> creators = new ArrayList<>();
        for (Method candidate : type.getDeclaredMethods()) {
            int modifiers = candidate.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || candidate.isSynthetic()) {
                continue;
            }
            if (type.isAssignableFrom(candidate.getReturnType())
                && ReflectionAnnotations.metadataOf(candidate).hasStereotype(Creator.class)) {
                creators.add(candidate);
            }
        }
        if (creators.size() < 2) {
            Method selected = creators.isEmpty() ? null : creators.get(0);
            if (selected != null) {
                selected.trySetAccessible();
            }
            return selected;
        }
        // a no-argument factory loses to one taking parameters, as it does at compilation time
        List<Method> withArguments = creators.stream().filter(candidate -> candidate.getParameterCount() > 0).toList();
        Method selected = withArguments.size() == 1
            ? withArguments.get(0)
            : withArguments.stream().filter(candidate -> Modifier.isPublic(candidate.getModifiers())).findFirst().orElse(null);
        if (selected != null) {
            selected.trySetAccessible();
        }
        return selected;
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable Constructor<T> selectConstructor(Class<T> beanType) {
        Constructor<?>[] declared = beanType.getDeclaredConstructors();
        if (beanType.isRecord()) {
            // the canonical constructor is the one a record is built by; another one it declares delegates to it
            Class<?>[] components = Arrays.stream(beanType.getRecordComponents())
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
            for (Constructor<?> candidate : declared) {
                if (Arrays.equals(candidate.getParameterTypes(), components)) {
                    candidate.trySetAccessible();
                    return (Constructor<T>) candidate;
                }
            }
        }
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

    private List<BeanProperty<T, Object>> discoverProperties() {
        Map<String, PropertyMembers> candidates = new LinkedHashMap<>();
        // the accessors first, as the processor resolves them: a field is a member of a property they
        // discovered, it makes one of its own only when field access is asked for
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                // an accessor of any visibility is a member of the property: what it declares holds
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                String name = method.getName();
                int parameters = method.getParameterCount();
                // a method annotated @Introspected.Property is an accessor whatever its name, of the property
                // named after the method when the name carries no accessor prefix, as the processor names it
                Introspected.Property declared = method.getAnnotation(Introspected.Property.class);
                if (parameters == 0 && method.getReturnType() != void.class) {
                    String property = accessorProperty(name, method.getReturnType());
                    if (property == null && declared != null) {
                        property = name;
                    }
                    if (property != null) {
                        candidate(candidates, property).addGetter(method, declared);
                    }
                } else if (parameters == 1) {
                    String property = writerProperty(name);
                    if (property == null && declared != null) {
                        property = name;
                    }
                    if (property != null) {
                        candidate(candidates, property).addSetter(method, declared);
                    }
                }
            }
        }
        // the accessor of a record component is a getter under the name of the component, which the naming rules
        // below do not match: an annotation of the component whose target is a method lands there and nowhere else
        if (beanType.isRecord()) {
            for (RecordComponent component : beanType.getRecordComponents()) {
                candidate(candidates, component.getName()).addComponent(component);
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
                    candidate(candidates, property).addGetter(method);
                }
            }
        }
        boolean fieldAccess = accessKinds.contains(Introspected.AccessKind.FIELD);
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Introspected.Property declared = field.getAnnotation(Introspected.Property.class);
                PropertyMembers property = candidates.get(field.getName());
                if (property != null) {
                    property.addField(field, declared);
                } else if (declared != null || (fieldAccess && isVisible(field))) {
                    // a field annotated @Introspected.Property makes a property whatever the access kinds
                    candidate(candidates, field.getName()).addField(field, declared);
                }
            }
        }
        List<BeanProperty<T, Object>> discovered = new ArrayList<>(candidates.size());
        for (Map.Entry<String, PropertyMembers> entry : candidates.entrySet()) {
            PropertyMembers property = entry.getValue();
            property.resolve();
            if (!isProperty(property) || !isIncluded(property)) {
                continue;
            }
            discovered.add(new ReflectionProperty<>(this, property, describeAnnotations, property.methodAccess(accessKinds.contains(Introspected.AccessKind.METHOD))));
        }
        return discovered;
    }

    private PropertyMembers candidate(Map<String, PropertyMembers> candidates, String name) {
        return candidates.computeIfAbsent(name, key -> new PropertyMembers(key, beanType));
    }

    /**
     * Whether the members of a name make a property: an accessor of a visibility the type admits, a field when
     * field access is asked for, or the accessor of a record component, which describes the component whatever
     * the kinds asked for. A private accessor describes the property it is a member of, it does not make one.
     */
    private boolean isProperty(PropertyMembers property) {
        if (property.isRecordComponent() || property.isDeclared()) {
            return true;
        }
        if (accessKinds.contains(Introspected.AccessKind.METHOD)) {
            for (Method accessor : property.accessors()) {
                if (isVisible(accessor)) {
                    return true;
                }
            }
        }
        if (accessKinds.contains(Introspected.AccessKind.FIELD)) {
            for (Field field : property.declaredFields()) {
                if (isVisible(field)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the type means the property to be described: it is not one {@link Introspected#excludes()} names,
     * it is one of the ones {@link Introspected#includes()} names where it names any, and it carries none of
     * the annotations {@link Introspected#excludedAnnotations()} names.
     *
     * <p>{@link Introspected#includedAnnotations()} is not applied. Its javadoc reads as a property filter, but
     * the processor uses it to choose the classes to scan for the packages
     * {@link Introspected#packages()} names and nowhere else, so a generated description of a type naming it
     * carries every property all the same. Filtering on it here would leave out the properties a generated
     * description reports, which is the one thing a caller must not see on switching between the two.</p>
     */
    private boolean isIncluded(PropertyMembers property) {
        if (excludes.contains(property.name) || (!includes.isEmpty() && !includes.contains(property.name))) {
            return false;
        }
        if (excludedAnnotations.isEmpty()) {
            return true;
        }
        AnnotationMetadata metadata = property.annotationMetadata(property.argument());
        for (String excluded : excludedAnnotations) {
            if (metadata.hasStereotype(excluded)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a member is visible enough to make a property, the way {@link Introspected#visibility()} is
     * applied by the processor: everything but a private declaration by default.
     */
    private boolean isVisible(Member member) {
        if (visibility.contains(Introspected.Visibility.ANY)) {
            return true;
        }
        if (visibility.contains(Introspected.Visibility.DEFAULT)) {
            return !Modifier.isPrivate(member.getModifiers());
        }
        return Modifier.isPublic(member.getModifiers());
    }

    /**
     * The property a method reads, by the read prefixes of the type: {@code getName} reads {@code name}, and
     * under the default prefix so does {@code isActive} when it answers a boolean. An empty prefix names a
     * fluent accessor, a method named after the property it reads.
     */
    @Nullable
    private String accessorProperty(String name, Class<?> returnType) {
        for (String prefix : readPrefixes) {
            if (prefix.isEmpty()) {
                return name;
            }
            if (name.startsWith(prefix) && name.length() > prefix.length() && Character.isUpperCase(name.charAt(prefix.length()))) {
                return decapitalize(name.substring(prefix.length()));
            }
            if (AccessorsStyle.DEFAULT_READ_PREFIX.equals(prefix) && name.startsWith("is") && name.length() > 2
                && Character.isUpperCase(name.charAt(2)) && (returnType == boolean.class || returnType == Boolean.class)) {
                return decapitalize(name.substring(2));
            }
        }
        return null;
    }

    /**
     * The property a method writes, by the write prefixes of the type: {@code setName} writes {@code name}, and
     * under an empty prefix a method named after the property does.
     */
    @Nullable
    private String writerProperty(String name) {
        for (String prefix : writePrefixes) {
            if (prefix.isEmpty()) {
                return name;
            }
            if (name.startsWith(prefix) && name.length() > prefix.length() && Character.isUpperCase(name.charAt(prefix.length()))) {
                return decapitalize(name.substring(prefix.length()));
            }
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> mergeMetadata(Argument<?> argument, AnnotationMetadata additional, Argument<?>[] typeParameters) {
        AnnotationMetadata metadata = argument.getAnnotationMetadata();
        if (metadata.isEmpty() && !additional.isEmpty()) {
            metadata = additional;
        }
        if (argument instanceof GenericPlaceholder<?> placeholder) {
            // a property of type `T` stays a variable, as it is to a generated introspection
            return Argument.ofTypeVariable((Class) argument.getType(), argument.getName(), placeholder.getVariableName(), metadata, typeParameters);
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
                    || method.isSynthetic() || method.isBridge() || isGroovyObjectMethod(method)) {
                    continue;
                }
                bySignature.putIfAbsent(signature(method), method);
            }
        }
        // then the interfaces, for a default method the type inherits without overriding it and for the
        // methods a super interface declares: the processor reads the methods of the whole hierarchy
        for (Class<?> anInterface : allInterfaces(beanType)) {
            for (Method method : anInterface.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
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

    /**
     * Whether a method is one the Groovy compiler adds to every class it compiles - {@code getMetaClass},
     * {@code getProperty}, {@code invokeMethod} - to implement {@code groovy.lang.GroovyObject}. The processors
     * run before the compiler adds them and never see them, so a description of a Groovy class does not carry them.
     */
    static boolean isGroovyObjectMethod(Method method) {
        if ("groovy.lang.GroovyObject".equals(method.getDeclaringClass().getName())) {
            // a default method of the interface itself, inherited by the class
            return true;
        }
        for (Class<?> anInterface : method.getDeclaringClass().getInterfaces()) {
            if ("groovy.lang.GroovyObject".equals(anInterface.getName())) {
                for (Method declared : anInterface.getMethods()) {
                    if (declared.getName().equals(method.getName()) && Arrays.equals(declared.getParameterTypes(), method.getParameterTypes())) {
                        return true;
                    }
                }
            }
        }
        return false;
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
    @Internal
    private static final class PropertyMembers {

        private final String name;
        private final Class<?> beanType;
        private final List<Field> fields = new ArrayList<>(1);
        private final List<Method> getters = new ArrayList<>(1);
        private final List<Method> setters = new ArrayList<>(1);
        private @Nullable RecordComponent component;
        private boolean resolved;
        private @Nullable Field field;
        private @Nullable Method getter;
        private @Nullable Method setter;
        // what @Introspected.Property declares on a member: that the property exists whatever the access kinds
        // and the visibility, the ways it is accessed, and the one member ignoring the other accessors
        private boolean declared;
        private boolean declaredAccessor;
        private @Nullable Set<Introspected.Property.Access> access;
        private @Nullable Member exclusive;

        PropertyMembers(String name, Class<?> beanType) {
            this.name = name;
            this.beanType = beanType;
        }

        void addField(Field field, Introspected.@Nullable Property declaration) {
            fields.add(field);
            declare(field, declaration);
        }

        void addGetter(Method getter) {
            getters.add(getter);
        }

        void addGetter(Method getter, Introspected.@Nullable Property declaration) {
            getters.add(getter);
            declare(getter, declaration);
        }

        void addComponent(RecordComponent recordComponent) {
            component = recordComponent;
            getters.add(recordComponent.getAccessor());
        }

        void addSetter(Method setter, Introspected.@Nullable Property declaration) {
            setters.add(setter);
            declare(setter, declaration);
        }

        private void declare(Member member, Introspected.@Nullable Property declaration) {
            if (declaration == null) {
                return;
            }
            declared = true;
            if (member instanceof Method) {
                declaredAccessor = true;
            }
            if (access == null) {
                // the first declaration says how the property is accessed; the processor rejects a second one
                // that disagrees
                access = Set.copyOf(Arrays.asList(declaration.accessKind()));
            }
            if (declaration.ignoreOtherAccessors() && exclusive == null) {
                exclusive = member;
            }
        }

        boolean isRecordComponent() {
            return component != null;
        }

        /**
         * Whether a member declares the property through {@link Introspected.Property}, which makes it one
         * whatever the access kinds and the visibility of the type.
         */
        boolean isDeclared() {
            return declared;
        }

        /**
         * Whether the property is read and written through its accessors: when method access is asked for,
         * or when an accessor is declared {@link Introspected.Property} - unless a field declared so ignores
         * the other accessors, in which case the value goes through the field.
         */
        boolean methodAccess(boolean methodAccessKind) {
            if (exclusive instanceof Field) {
                return false;
            }
            return methodAccessKind || declaredAccessor;
        }

        boolean readable() {
            return access == null || access.contains(Introspected.Property.Access.READ);
        }

        boolean writable() {
            return access == null || access.contains(Introspected.Property.Access.WRITE);
        }

        /**
         * Selects the members the property is read and written through, and drops the setters the value of the
         * property cannot be given to. Reflection reports the declarations of a class in no order, so the
         * choice is made on the declarations themselves and is the same on every JVM.
         */
        void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            if (exclusive instanceof Method method) {
                // the accessor ignoring the other accessors is the only one of its kind
                if (getters.contains(method)) {
                    getters.removeIf(candidate -> !candidate.equals(method));
                }
                if (setters.contains(method)) {
                    setters.removeIf(candidate -> !candidate.equals(method));
                }
            }
            // a field is declared once per class, and the classes are walked from the most derived one
            field = fields.isEmpty() ? null : fields.get(0);
            getter = selectGetter();
            if (getter != null && !setters.isEmpty()) {
                // a setter of another type is not the setter of this property: the processor drops it rather
                // than writing through it, and only when a getter says what the property holds
                Class<?> readType = getter.getReturnType();
                setters.removeIf(candidate -> isIncompatibleSetter(readType, candidate));
            }
            setter = selectSetter();
        }

        @Nullable Field field() {
            return field;
        }

        @Nullable Method getter() {
            return getter;
        }

        @Nullable Method setter() {
            return setter;
        }

        /**
         * The accessors of the property, the ones an incompatible parameter dropped excluded.
         */
        List<Method> accessors() {
            List<Method> accessors = new ArrayList<>(getters.size() + setters.size());
            accessors.addAll(getters);
            accessors.addAll(setters);
            return accessors;
        }

        List<Field> declaredFields() {
            return fields;
        }

        /**
         * The most specific getter: the one declaring the narrowest return type, which is what a covariant
         * override declares, {@code isX} before {@code getX} of a boolean as the naming rules generate it,
         * then the declaration of the most derived type and then the name.
         */
        private @Nullable Method selectGetter() {
            if (getters.size() < 2) {
                return getters.isEmpty() ? null : getters.get(0);
            }
            List<Method> ordered = new ArrayList<>(getters);
            ordered.sort(Comparator.comparingInt((Method candidate) -> candidate.getName().startsWith("is") ? 0 : 1)
                .thenComparingInt(candidate -> rankOf(candidate.getDeclaringClass()))
                .thenComparing(Method::getName));
            Method selected = ordered.get(0);
            for (Method candidate : ordered) {
                if (selected.getReturnType() != candidate.getReturnType()
                    && selected.getReturnType().isAssignableFrom(candidate.getReturnType())) {
                    selected = candidate;
                }
            }
            return selected;
        }

        /**
         * The setter taking the narrowest parameter the property value can be given to, then the declaration
         * of the most derived type and then the name: an overload taking a wider type writes the same
         * property, and a generated introspection writes through the narrow one.
         */
        private @Nullable Method selectSetter() {
            if (setters.size() < 2) {
                return setters.isEmpty() ? null : setters.get(0);
            }
            List<Method> ordered = new ArrayList<>(setters);
            ordered.sort(Comparator.comparingInt((Method candidate) -> rankOf(candidate.getDeclaringClass()))
                .thenComparing(Method::getName));
            Method selected = ordered.get(0);
            for (Method candidate : ordered) {
                Class<?> selectedType = selected.getParameterTypes()[0];
                Class<?> candidateType = candidate.getParameterTypes()[0];
                if (selectedType != candidateType && selectedType.isAssignableFrom(candidateType)) {
                    selected = candidate;
                }
            }
            return selected;
        }

        /**
         * Whether the value of the property cannot be given to the parameter of a setter, the way
         * {@code AstBeanPropertiesUtils.isIncompatibleSetterType} tells a setter of another type from the
         * setter of this property: the parameter takes what the property holds - a {@code setValue(Object)}
         * of a {@code String} property is the setter of that property - and a parameter of the very type of
         * the property is one whatever the assignability of the two erasures says.
         */
        private static boolean isIncompatibleSetter(Class<?> readType, Method setter) {
            Class<?> parameterType = setter.getParameterTypes()[0];
            if (parameterType == readType) {
                return false;
            }
            return !ReflectionUtils.getWrapperType(parameterType).isAssignableFrom(ReflectionUtils.getWrapperType(readType));
        }

        /**
         * The rank of the type declaring a member: the bean type first, then its super classes, then the
         * interfaces, so that the declaration of the most derived type is the one that wins.
         */
        private int rankOf(Class<?> declaringType) {
            int rank = 0;
            for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
                if (type == declaringType) {
                    return rank;
                }
                rank++;
            }
            int index = allInterfaces(beanType).indexOf(declaringType);
            return index == -1 ? Integer.MAX_VALUE : rank + index;
        }

        /**
         * The argument of the property: its type and type arguments with their type-use annotations. Every
         * declaration can annotate the type arguments — {@code List<@Size String>} on the field, on the
         * return type of the getter of an interface — so the arguments of all of them are merged, the
         * selected member first because it declares the type of the property.
         */
        Argument<?> argument() {
            Argument<?> merged = null;
            for (Argument<?> argument : candidateArguments()) {
                merged = merged == null ? argument : mergeTypeArguments(merged, argument);
            }
            if (merged == null) {
                throw new IllegalStateException("The property '" + name + "' has no member");
            }
            return merged;
        }

        /**
         * The type of the property as every member declares it, read through the bean type: a variable the
         * declaring type leaves open is the type the bean type gives it, which is what the processors generate.
         */
        private List<Argument<?>> candidateArguments() {
            List<Argument<?>> arguments = new ArrayList<>(fields.size() + getters.size() + setters.size());
            for (Field candidate : selectedFirst(fields, field)) {
                arguments.add(ReflectionArguments.of(name, candidate, beanType));
            }
            for (Method candidate : selectedFirst(getters, getter)) {
                arguments.add(ReflectionArguments.returnOf(name, candidate, beanType));
            }
            for (Method candidate : selectedFirst(setters, setter)) {
                // the type of the parameter, not what the parameter declares: an annotation written on the
                // parameter annotates the value passed to the setter, and a generated property does not carry it
                arguments.add(ReflectionArguments.ofType(name, candidate.getParameters()[0], beanType));
            }
            return arguments;
        }

        /**
         * The members with the selected one first: it declares the type of the property, the others complete
         * the annotations of its type arguments.
         */
        private static <M> List<M> selectedFirst(List<M> members, @Nullable M selected) {
            if (selected == null || members.size() < 2 || members.get(0) == selected) {
                return members;
            }
            List<M> ordered = new ArrayList<>(members.size());
            ordered.add(selected);
            for (M member : members) {
                if (member != selected) {
                    ordered.add(member);
                }
            }
            return ordered;
        }

        /**
         * The members with their own metadata, in the order a generated introspection writes them - the
         * fields, then the read methods, then the write methods - a shadowed or overridden declaration after
         * the one that hides it. A generated introspection carries the selected member of each kind alone;
         * reflection reads the whole hierarchy, and the constraints of every declaration apply.
         *
         * @param <B> The bean type
         */
        <B> List<BeanPropertyMember<B, ?>> members() {
            List<BeanPropertyMember<B, ?>> members = new ArrayList<>(fields.size() + getters.size() + setters.size());
            for (Field field : fields) {
                members.add(member(ElementType.FIELD, field.getDeclaringClass(), field.getName(),
                    withType(ReflectionAnnotations.metadataOf(field), field.getAnnotatedType()),
                    ReflectionArguments.of(name, field, beanType),
                    field));
            }
            for (Method getter : getters) {
                members.add(member(ElementType.METHOD, getter.getDeclaringClass(), getter.getName(),
                    withType(ReflectionAnnotations.metadataOf(getter), getter.getAnnotatedReturnType()),
                    ReflectionArguments.returnOf(name, getter, beanType),
                    getter));
            }
            for (Method setter : setters) {
                members.add(member(ElementType.METHOD, setter.getDeclaringClass(), setter.getName(),
                    ReflectionAnnotations.metadataOf(setter, setter.getParameters()[0]),
                    ReflectionArguments.of(name, setter.getParameters()[0], beanType),
                    setter));
            }
            return List.copyOf(members);
        }

        /**
         * One member, its argument carrying the metadata of the member: the processor writes the argument of a
         * member from the type of the member with the annotations of the member applied to it, so the argument
         * answers what the member declares and not only what its type does.
         */
        private <B> BeanPropertyMember<B, ?> member(ElementType elementType,
                                                    Class<?> declaringType,
                                                    String memberName,
                                                    AnnotationMetadata metadata,
                                                    Argument<?> argument,
                                                    AnnotatedElement member) {
            return new ReflectionPropertyMember<>(elementType, declaringType, memberName, metadata,
                Argument.of(argument.getType(), name, metadata, argument.getTypeParameters()), member);
        }

        private static AnnotationMetadata withType(AnnotationMetadata member, AnnotatedType type) {
            AnnotationMetadata typeMetadata = ReflectionAnnotations.metadataOf(type);
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
            // the accessors of a property are read before its field and the more specific member wins, as the
            // processors read them: an annotation the getter declares is the one the property carries, and the
            // field completes what no accessor declares rather than adding to it. Adding every member instead
            // lets a farther site override a nearer one and collects one occurrence of a repeatable annotation
            // per site, where a generated property carries only the occurrence of the member it was read from.
            // The parameter of a setter is not a site of the property at all: it annotates the value being
            // passed, and a generated property does not carry it
            AnnotationMetadata metadata = AnnotationMetadata.EMPTY_METADATA;
            // the component of a record is the site the source writes, and the only one an annotation targeting
            // ElementType.RECORD_COMPONENT lands on: javac copies an annotation to the field, the accessor and
            // the constructor parameter only where the target admits it, so the component is read first
            if (component != null) {
                metadata = ReflectionAnnotations.merge(metadata, ReflectionAnnotations.metadataOf(component));
            }
            for (Method candidate : selectedFirst(getters, getter)) {
                metadata = ReflectionAnnotations.merge(metadata, ReflectionAnnotations.metadataOf(candidate));
            }
            for (Method candidate : selectedFirst(setters, setter)) {
                metadata = ReflectionAnnotations.merge(metadata, ReflectionAnnotations.metadataOf(candidate));
            }
            for (Field candidate : selectedFirst(fields, field)) {
                metadata = ReflectionAnnotations.merge(metadata, ReflectionAnnotations.metadataOf(candidate));
            }
            // the argument of the property carries the annotations of the member it was read from as well as
            // the type-use ones, so it completes the members rather than overriding them: what an accessor
            // declares stays what the property carries
            return ReflectionAnnotations.merge(metadata, typed.getAnnotationMetadata());
        }
    }

    /**
     * The selected constructor as a bean constructor: instantiating through it checks the arguments as
     * {@link #instantiate(boolean, Object...)} does.
     */
    @Internal
    private final class SelectedBeanConstructor implements BeanConstructor<T> {

        private final AnnotationMetadata metadata = factory != null
            ? ReflectionAnnotations.metadataOf(factory)
            : constructor == null ? AnnotationMetadata.EMPTY_METADATA : ReflectionAnnotations.metadataOf(constructor);

        @Override
        public Class<T> getDeclaringBeanType() {
            return beanType;
        }

        @Override
        public Argument<?>[] getArguments() {
            return constructorArguments;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return metadata;
        }

        @Override
        public T instantiate(@Nullable Object... parameterValues) {
            return ReflectionBeanIntrospection.this.instantiate(true, parameterValues);
        }
    }

    /**
     * A property read and written through its members.
     *
     * @param <B> The bean type
     * @param <P> The property type
     */
    @Internal
    private static final class ReflectionProperty<B, P> extends AbstractBeanProperty<B, P> {

        private final @Nullable Field field;
        private final @Nullable Method getter;
        private final @Nullable Method setter;
        private final PropertyMembers members;
        // the argument the property was typed from, a placeholder when the property is of a variable type
        private final Argument<?> typed;
        // whether the accessors are the way to the value: when method access is not asked for, an accessor
        // still describes the property, but the value is read and written through the field, as a generated
        // introspection with field access alone does
        private final boolean methodAccess;
        // what @Introspected.Property allows: a property declared readable alone is write-only to the caller
        // whatever members it has, and one declared writable alone read-only
        private final boolean readable;
        private final boolean writable;
        // the members are described on the first call and shared from then on, so a description that never
        // asks for them costs nothing; describing the same list twice under a race is harmless
        @SuppressWarnings("java:S3077") // members() returns an immutable List.copyOf
        private volatile @Nullable List<BeanPropertyMember<B, ?>> propertyMembers;

        ReflectionProperty(BeanIntrospection<B> introspection, PropertyMembers members, boolean describeAnnotations, boolean methodAccess) {
            this(introspection, members, members.argument(), describeAnnotations, methodAccess);
        }

        @SuppressWarnings("unchecked")
        private ReflectionProperty(BeanIntrospection<B> introspection,
                                   PropertyMembers members,
                                   Argument<?> typed,
                                   boolean describeAnnotations,
                                   boolean methodAccess) {
            super(introspection, (Class<P>) typed.getType(), members.name,
                describeAnnotations ? members.annotationMetadata(typed) : AnnotationMetadata.EMPTY_METADATA,
                typed.getTypeParameters());
            this.members = members;
            this.typed = typed;
            this.methodAccess = methodAccess;
            this.readable = members.readable();
            this.writable = members.writable();
            this.field = members.field();
            this.getter = members.getter();
            this.setter = members.setter();
            // a member of a type that is not public is reachable all the same: what it declares is what the
            // property carries, and the accessor is the only way to the value
            if (field != null) {
                field.trySetAccessible();
            }
            if (getter != null) {
                getter.trySetAccessible();
            }
            if (setter != null) {
                setter.trySetAccessible();
            }
        }

        /**
         * The members this property is made of, each with its own metadata, its own argument and the field or
         * method reflection read it from.
         *
         * <p>A generated introspection carries them only when {@link Introspected#members()} asks for them,
         * as they grow the class it writes. Reflection writes nothing and describes a member only when one is
         * asked for, so they are always reported here - which is what a type the processors never saw needs,
         * since it carries no annotation to ask with.</p>
         *
         * @return The members, the fields first, then the read methods, then the write methods
         */
        @Override
        public List<BeanPropertyMember<B, ?>> getMembers() {
            List<BeanPropertyMember<B, ?>> resolved = propertyMembers;
            if (resolved == null) {
                resolved = members.members();
                propertyMembers = resolved;
            }
            return resolved;
        }

        /**
         * The argument of the property, a placeholder when the property is of a variable type - {@code T} of a
         * {@code Box<T>} - as the argument a generated property answers is one.
         */
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Argument<P> asArgument() {
            if (typed instanceof GenericPlaceholder<?> placeholder) {
                return Argument.ofTypeVariable((Class) getType(), getName(), placeholder.getVariableName(), getAnnotationMetadata(), typed.getTypeParameters());
            }
            return super.asArgument();
        }

        @Override
        public boolean isReadOnly() {
            return !writable || (setter == null && (field == null || Modifier.isFinal(field.getModifiers())));
        }

        @Override
        public boolean isWriteOnly() {
            return !readable || (getter == null && field == null);
        }

        @Override
        @SuppressWarnings({"unchecked", "NullAway"}) // a property value can be null, the declaration of readInternal predates the nullness annotations
        protected P readInternal(B bean) {
            if (readable && getter != null && (methodAccess || field == null)) {
                return ReflectionUtils.invokeMethod(bean, getter);
            }
            if (field == null || !readable) {
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
            if (!writable) {
                throw new UnsupportedOperationException("The property '" + getName() + "' of " + getDeclaringType().getName() + " is read only");
            }
            if (setter != null && (methodAccess || field == null || Modifier.isFinal(field.getModifiers()))) {
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
     * A member of a property read through reflection: the field itself for a field member rather than the
     * getter of the property, the way a constraint declared on a field is validated against what that field
     * holds.
     *
     * @param <B> The bean type
     */
    @Internal
    private static final class ReflectionPropertyMember<B> implements ReflectivePropertyMember<B> {

        private final ElementType elementType;
        private final Class<?> declaringType;
        private final String name;
        private final AnnotationMetadata annotationMetadata;
        private final Argument<Object> argument;
        private final AnnotatedElement member;

        @SuppressWarnings("unchecked")
        ReflectionPropertyMember(ElementType elementType,
                                 Class<?> declaringType,
                                 String name,
                                 AnnotationMetadata annotationMetadata,
                                 Argument<?> argument,
                                 AnnotatedElement member) {
            this.elementType = elementType;
            this.declaringType = declaringType;
            this.name = name;
            this.annotationMetadata = annotationMetadata;
            this.argument = (Argument<Object>) argument;
            this.member = member;
            // a member of a type that is not public is reachable all the same, as it is for the property
            if (member instanceof Field field) {
                field.trySetAccessible();
            } else if (member instanceof Method method) {
                method.trySetAccessible();
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }

        @Override
        public Class<?> getDeclaringType() {
            return declaringType;
        }

        @Override
        public Argument<Object> asArgument() {
            return argument;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public AnnotatedElement getMember() {
            return member;
        }

        @Override
        public boolean isReadable() {
            return member instanceof Field || (member instanceof Method method && method.getParameterCount() == 0);
        }

        @Override
        public @Nullable Object read(B bean) {
            ArgumentUtils.requireNonNull("bean", bean);
            if (!declaringType.isInstance(bean)) {
                throw new IllegalArgumentException("Invalid bean [" + bean + "] for type: " + declaringType);
            }
            if (member instanceof Field field) {
                try {
                    return field.get(bean);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read the field '" + name + "' of " + declaringType.getName(), e);
                }
            }
            if (member instanceof Method method && method.getParameterCount() == 0) {
                return ReflectionUtils.invokeMethod(bean, method);
            }
            throw new UnsupportedOperationException("Cannot read from the property member: " + name);
        }

        @Override
        public String toString() {
            return "BeanPropertyMember{elementType=" + elementType
                + ", declaringType=" + declaringType
                + ", name='" + name + "'}";
        }
    }

    /**
     * A bean method dispatching to a {@link ReflectionExecutableMethod}.
     *
     * @param <B> The bean type
     * @param <R> The return type
     */
    @Internal
    private static final class ReflectionMethod<B, R> extends AbstractBeanMethod<B, R> {

        private final ReflectionExecutableMethod<B, R> executable;

        @SuppressWarnings("unchecked")
        ReflectionMethod(BeanIntrospection<B> introspection, ReflectionExecutableMethod<B, R> executable) {
            this(introspection, executable, methodMetadata(introspection, executable));
        }

        @SuppressWarnings("unchecked")
        private ReflectionMethod(BeanIntrospection<B> introspection,
                                 ReflectionExecutableMethod<B, R> executable,
                                 AnnotationMetadata metadata) {
            super(introspection,
                (Argument<R>) resolvedReturn(executable, introspection.getBeanType()),
                executable.getMethodName(),
                metadata,
                ReflectionArguments.argumentsOf(executable.getMethod(), introspection.getBeanType()));
            this.executable = executable;
        }

        /**
         * The metadata of the method over the metadata of the bean type. The processor describes a bean method
         * from an element that reads the annotations of its owning class too, and a generated bean method
         * reports them the same way: an annotation the type carries is present on the method but is not among
         * the ones it declares, one the method declares wins over the same annotation on the type, and an
         * annotation written repeatedly on both is read back as the occurrences of the two together.
         */
        private static AnnotationMetadata methodMetadata(BeanIntrospection<?> introspection,
                                                         ReflectionExecutableMethod<?, ?> executable) {
            AnnotationMetadata type = introspection.getAnnotationMetadata();
            AnnotationMetadata method = executable.getAnnotationMetadata();
            if (type.isEmpty()) {
                return method;
            }
            return method.isEmpty() ? type : new AnnotationMetadataHierarchy(type, method);
        }

        /**
         * The return type as the bean type sees it: a variable the type declaring the method leaves open is
         * the type the bean type gives it rather than the bound of the variable, which is what a generated
         * bean method reports. It carries what the return type itself carries - the annotations of the type
         * and the ones declaring the variable it stands for - and not the metadata of the method, as a
         * generated bean method reports the argument the processor writes for the return type alone.
         */
        private static Argument<?> resolvedReturn(ReflectionExecutableMethod<?, ?> executable, Class<?> beanType) {
            return ReflectionArguments.returnOf(executable.getMethod(), beanType);
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

        @Override
        @SuppressWarnings("NullAway") // a method can return null, the declaration of invokeInternal predates the nullness annotations
        protected R invokeInternal(B instance, @Nullable Object... arguments) {
            return executable.invoke(instance, arguments);
        }
    }

    /**
     * A builder collecting the constructor arguments by name or index.
     */
    @Internal
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
