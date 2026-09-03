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

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.annotation.AnnotationMetadataException;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds {@link AnnotationMetadata} and {@link AnnotationValue}s from the annotations read reflectively from an
 * {@link AnnotatedElement}, in the shape the annotation processors give the metadata they generate at
 * compilation time.
 *
 * <p>Code written against generated metadata then works unchanged for an element that has none:</p>
 * <ul>
 *     <li>the meta-annotations of an annotation are its stereotypes, recursively, with their values;</li>
 *     <li>a repeatable annotation is filed under its container, whether it was written once, several times or
 *     inside the container, and the container is registered so that
 *     {@link AnnotationMetadata#getAnnotationValuesByType(Class)} finds it;</li>
 *     <li>the defaults of the members are registered and reachable through
 *     {@link AnnotationMetadata#getDefaultValues(String)} and the value accessors;</li>
 *     <li>a class value is an {@link AnnotationClassValue}, an enum value is its name, a nested annotation is an
 *     {@link AnnotationValue};</li>
 *     <li>the members annotated {@link NonBinding} are recorded, so that a qualifier built from the metadata
 *     ignores them;</li>
 *     <li>an annotation a class inherits through {@link Inherited} is present but not declared, from a super
 *     class as from an interface of its hierarchy, which the processors walk and {@link Class#getAnnotations()}
 *     leaves out.</li>
 * </ul>
 *
 * <p>An annotation instance does not say which members were written, so a member whose value equals its
 * default is not part of the values of the annotation; it is served by the defaults, as a member that was not
 * written is at compilation time.</p>
 *
 * <p>The annotation mappers, transformers and remappers of the annotation processors are not applied: they
 * are part of {@code micronaut-core-processor} and are not on a runtime classpath. The
 * {@link ReflectionAnnotationCustomizer} services are their runtime counterpart, and receive the values of
 * every annotation this class converts.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionAnnotations {

    private static final String JAVA_LANG_ANNOTATION = "java.lang.annotation.";
    private static final String KOTLIN = "kotlin.";
    private static final List<ReflectionAnnotationCustomizer> CUSTOMIZERS =
        SoftServiceLoader.load(ReflectionAnnotationCustomizer.class, ReflectionAnnotations.class.getClassLoader()).collectAll();

    /**
     * The members of an annotation type, made accessible - the type may not be public - and sorted by name:
     * {@link Class#getDeclaredMethods()} gives no order, and the members of the metadata are to come out the
     * same on every run.
     */
    private static final ClassValue<List<Method>> MEMBERS = new ClassValue<>() {
        @Override
        protected List<Method> computeValue(Class<?> type) {
            List<Method> members = new ArrayList<>();
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && !method.isSynthetic() && method.getParameterCount() == 0) {
                    method.trySetAccessible();
                    members.add(method);
                }
            }
            members.sort(Comparator.comparing(Method::getName));
            return Collections.unmodifiableList(members);
        }
    };

    /**
     * The defaults of an annotation type, converted in the shapes the values are given, registered with the
     * shared registry the value accessors consult on first use.
     */
    private static final ClassValue<Map<CharSequence, Object>> DEFAULTS = new ClassValue<>() {
        @Override
        protected Map<CharSequence, Object> computeValue(Class<?> type) {
            Map<CharSequence, Object> defaults = new LinkedHashMap<>();
            for (Method member : MEMBERS.get(type)) {
                Object defaultValue = member.getDefaultValue();
                if (defaultValue != null) {
                    defaults.put(member.getName(), convert(defaultValue));
                }
            }
            // keyed by name and not by class: a type loaded by several class loaders - the same annotation in
            // several deployments - must resolve to the class of the loader asking for it, not to the first one seen
            DefaultAnnotationMetadata.registerAnnotationDefaults(type.getName(), defaults);
            return Collections.unmodifiableMap(defaults);
        }
    };

    private ReflectionAnnotations() {
    }

    /**
     * Builds the metadata of an element.
     *
     * @param element The element
     * @return The metadata, {@link AnnotationMetadata#EMPTY_METADATA} when the element has no annotation
     */
    public static AnnotationMetadata metadataOf(AnnotatedElement element) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        add(metadata, element);
        return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
    }

    /**
     * Builds the metadata of several elements merged, the first element first: the field, the getter and the
     * setter of a property, or a parameter and its type.
     *
     * @param elements The elements, a {@code null} element skipped
     * @return The metadata, {@link AnnotationMetadata#EMPTY_METADATA} when none of the elements has an annotation
     */
    public static AnnotationMetadata metadataOf(@Nullable AnnotatedElement... elements) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        for (AnnotatedElement element : elements) {
            if (element != null) {
                add(metadata, element);
            }
        }
        return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
    }

    /**
     * Builds the metadata of annotation instances, each declared: the annotations a specification API hands
     * over as an array, such as the ones of a JAX-RS entity or the qualifiers of a CDI lookup.
     *
     * @param annotations The annotations
     * @return The metadata, {@link AnnotationMetadata#EMPTY_METADATA} when there is no annotation
     */
    public static AnnotationMetadata metadataOf(Annotation... annotations) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        for (Annotation annotation : annotations) {
            addAnnotation(metadata, annotation, true);
        }
        return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
    }

    /**
     * The metadata of an element that declares an annotation type, without an instance of it: the annotation
     * with the values given, its defaults and its stereotypes, as the processors record it.
     *
     * <p>Code that adapts another container - a Guice binding annotation, a Spring bean marked primary - knows
     * the annotation type and the members it means, and has no instance to read. Declaring the annotation by
     * name alone loses its defaults and its stereotypes, and a qualifier built from such metadata then fails to
     * match one built from the generated metadata of a bean.</p>
     *
     * @param annotationType The annotation type, filed under its container when it is repeatable
     * @param values         The values of the members, empty when the annotation is written bare
     * @return The metadata
     */
    public static AnnotationMetadata declaring(Class<? extends Annotation> annotationType, Map<CharSequence, Object> values) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        declare(metadata, annotationType, values);
        return metadata;
    }

    /**
     * The metadata of an element that declares an annotation type with the defaults of its members.
     *
     * @param annotationType The annotation type
     * @return The metadata
     * @see #declaring(Class, Map)
     */
    public static AnnotationMetadata declaring(Class<? extends Annotation> annotationType) {
        return declaring(annotationType, Map.of());
    }

    /**
     * Adds an annotation type, the values given, its defaults and its stereotypes to a metadata under
     * construction.
     *
     * @param metadata       The metadata
     * @param annotationType The annotation type
     * @param values         The values of the members, empty when the annotation is written bare
     * @see #declaring(Class, Map)
     */
    public static void declare(MutableAnnotationMetadata metadata,
                               Class<? extends Annotation> annotationType,
                               Map<CharSequence, Object> values) {
        register(metadata, annotationType);
        String name = annotationType.getName();
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable == null) {
            metadata.addDeclaredAnnotation(name, new LinkedHashMap<>(values));
        } else {
            // a repeatable annotation is filed under its container, whether it is written once or several times
            Class<? extends Annotation> container = repeatable.value();
            register(metadata, container);
            DefaultAnnotationMetadata.registerRepeatableAnnotations(Map.of(name, container.getName()));
            metadata.addDeclaredRepeatable(container.getName(),
                new AnnotationValue<>(name, new LinkedHashMap<>(values), defaultValues(annotationType)));
        }
        addStereotypes(metadata, annotationType, List.of(name), true);
    }

    /**
     * The annotations of two metadata in one, both declared: the metadata of a class together with the
     * annotations another container means it to carry.
     *
     * <p>A container adapting another one - a Spring bean it marks primary, a Guice binding it qualifies -
     * has annotations of its own to add to the ones a class declares. Replacing the metadata of the class
     * loses what the class says; a {@link AnnotationMetadataHierarchy hierarchy} keeps both but only the last
     * level counts as declared, and the framework reads a scope, a qualifier and a primary marker from the
     * declared level. This merges them, so both are declared.</p>
     *
     * @param first  The metadata whose values win where both declare the same annotation
     * @param second The metadata to add
     * @return The merged metadata
     */
    public static AnnotationMetadata merge(AnnotationMetadata first, AnnotationMetadata second) {
        if (second.isEmpty()) {
            return first;
        }
        if (first.isEmpty()) {
            return second;
        }
        // adding fills in the members the target does not carry and leaves the ones it does alone, so the
        // metadata whose values are to win is the one to start from
        MutableAnnotationMetadata merged = MutableAnnotationMetadata.of(first);
        merged.addAnnotationMetadata(MutableAnnotationMetadata.of(second));
        return merged;
    }

    /**
     * Adds the annotations of an element to a metadata under construction. An annotation whose values cannot be
     * read is left out, with a message logged at debug level: the generated metadata of an element records it,
     * and the element must not lose the annotations that can be read because of it.
     *
     * @param metadata The metadata
     * @param element  The element
     */
    public static void add(MutableAnnotationMetadata metadata, AnnotatedElement element) {
        Annotation[] declared = element.getDeclaredAnnotations();
        for (Annotation annotation : declared) {
            addAnnotationOf(metadata, element, annotation, true);
        }
        if (!(element instanceof Class<?> type)) {
            return;
        }
        // the names the annotations are filed under, so that one inherited neither displaces nor duplicates one
        // declared: a declared annotation wins over the same annotation inherited
        Set<String> present = new HashSet<>();
        for (Annotation annotation : declared) {
            present.add(filedUnder(annotation.annotationType()));
        }
        for (Annotation annotation : type.getAnnotations()) {
            if (present.add(filedUnder(annotation.annotationType()))) {
                addAnnotationOf(metadata, type, annotation, false);
            }
        }
        // getAnnotations() is built from the super class chain alone, so an @Inherited annotation an implemented
        // interface declares is missing from it, while the hierarchy the processors walk includes the interfaces
        for (Class<?> interfaceType : interfaceHierarchy(type)) {
            for (Annotation annotation : interfaceType.getDeclaredAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (isInheritable(annotationType) && present.add(filedUnder(annotationType))) {
                    addAnnotationOf(metadata, type, annotation, false);
                }
            }
        }
    }

    /**
     * Adds one annotation instance to a metadata under construction, as
     * {@link #add(MutableAnnotationMetadata, AnnotatedElement)} adds every annotation of an element: a caller
     * keeping only some annotations of an element - the constraints, the qualifiers - filters the instances and
     * adds the ones it keeps.
     *
     * @param metadata   The metadata
     * @param annotation The annotation
     * @param declared   Whether the annotation is declared on the element rather than inherited
     */
    public static void add(MutableAnnotationMetadata metadata, Annotation annotation, boolean declared) {
        addAnnotation(metadata, annotation, declared);
    }

    /**
     * The annotations a repeatable container holds.
     *
     * @param annotation The annotation
     * @return The contained annotations, empty when the annotation is not the container of a repeatable
     * annotation
     */
    public static List<Annotation> contained(Annotation annotation) {
        Annotation[] contained = containedAnnotations(annotation);
        return contained == null ? List.of() : List.of(contained);
    }

    /**
     * The values of the members of an annotation, as the metadata stores them: the members whose value is the
     * default of the member are left out, a class is an {@link AnnotationClassValue}, an enum constant is its
     * name, a nested annotation is an {@link AnnotationValue} and the members annotated {@link NonBinding} are
     * listed under {@link AnnotationUtil#NON_BINDING_ATTRIBUTE}.
     *
     * <p>A nested annotation is given the same treatment as the annotation holding it, recursively, and an
     * array value is a copy the caller owns.</p>
     *
     * @param annotation The annotation
     * @return The values, mutable
     * @throws IllegalStateException When a member of the annotation cannot be read
     */
    public static Map<CharSequence, Object> values(Annotation annotation) {
        Class<? extends Annotation> type = annotation.annotationType();
        // reading the instance and converting its members is the shared conversion of the core API, a nested
        // annotation and the members of it included; what this module adds on top of it is the policies below
        Map<CharSequence, Object> read = AnnotationValue.of(annotation).getValues();
        // a member equal to its default is left out of the values, as it is at compilation time: an instance
        // answers every one of its members while the processors record only what the source writes, and
        // registering the defaults is what lets the accessors serve the members left out
        Map<CharSequence, Object> defaults = defaultValues(type);
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        List<String> nonBinding = null;
        for (Method member : MEMBERS.get(type)) {
            String name = member.getName();
            if (member.isAnnotationPresent(NonBinding.class)) {
                if (nonBinding == null) {
                    nonBinding = new ArrayList<>(2);
                }
                nonBinding.add(name);
            }
            Object value = read.get(name);
            // the converted forms are compared, and by content: a member holding an array answers a fresh one.
            // both sides are the form the shared conversion gives, the defaults included, so the comparison is
            // made before the policies below reshape a nested annotation
            if (value == null || Objects.deepEquals(value, defaults.get(name))) {
                continue;
            }
            values.put(name, memberValue(annotation, member, value));
        }
        if (nonBinding != null) {
            // the attribute lists itself, as the processors record it
            nonBinding.add(AnnotationUtil.NON_BINDING_ATTRIBUTE);
            values.put(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBinding.toArray(String[]::new));
        }
        for (ReflectionAnnotationCustomizer customizer : CUSTOMIZERS) {
            if (customizer.supports(type)) {
                customizer.customize(annotation, values);
            }
        }
        return values;
    }

    /**
     * The defaults of the members of an annotation type, in the shapes {@link #values(Annotation)} gives the
     * values.
     *
     * @param annotationType The annotation type
     * @return The defaults, unmodifiable
     */
    public static Map<CharSequence, Object> defaultValues(Class<? extends Annotation> annotationType) {
        return DEFAULTS.get(annotationType);
    }

    /**
     * The annotation value of an annotation instance, carrying its defaults. An instance that is an
     * {@link AnnotationValueProvider} - a synthetic annotation built from a value - returns that value.
     *
     * @param annotation The annotation
     * @param <A>        The annotation type
     * @return The annotation value
     */
    public static <A extends Annotation> AnnotationValue<A> valueOf(A annotation) {
        return valueOf(annotation, null);
    }

    /**
     * The annotation value of an annotation instance, carrying its defaults, with its values customized.
     *
     * @param annotation The annotation
     * @param customizer The customizer of the values, receiving a mutable copy, can be {@code null}
     * @param <A>        The annotation type
     * @return The annotation value
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> AnnotationValue<A> valueOf(A annotation,
                                                                  @Nullable Consumer<Map<CharSequence, Object>> customizer) {
        if (customizer == null && annotation instanceof AnnotationValueProvider<?> provider) {
            return (AnnotationValue<A>) provider.annotationValue();
        }
        Class<A> type = (Class<A>) annotation.annotationType();
        Map<CharSequence, Object> values = values(annotation);
        if (customizer != null) {
            customizer.accept(values);
        }
        return new AnnotationValue<>(type.getName(), values, defaultValues(type));
    }

    /**
     * Builds an annotation instance from an annotation value: the reverse of {@link #valueOf(Annotation)}. The
     * instance implements {@link AnnotationValueProvider}, so that converting it back yields the value it was
     * built from, and its members are the values of the annotation value completed by the defaults of the type.
     *
     * @param annotationType  The annotation type
     * @param annotationValue The annotation value
     * @param <A>             The annotation type
     * @return The annotation
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A synthesize(Class<A> annotationType, AnnotationValue<A> annotationValue) {
        // register the defaults of the type, so that the members the value does not carry are served
        defaultValues(annotationType);
        try {
            return AnnotationMetadataSupport.buildAnnotation(annotationType, annotationValue);
        } catch (AnnotationMetadataException | IllegalArgumentException e) {
            // the shared path needs a proxy carrying AnnotationValueProvider, which cannot be built for every
            // annotation type - one that is not public, or whose loader cannot see that interface - and a
            // specification hands those over like any other
            return (A) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[]{annotationType},
                new SynthesizedAnnotation<>(annotationType, annotationValue));
        }
    }

    /**
     * Builds an annotation instance from an annotation value, the type resolved by name through a class loader.
     *
     * @param annotationValue The annotation value
     * @param classLoader     The class loader defining the annotation type
     * @param <A>             The annotation type
     * @return The annotation
     * @throws IllegalArgumentException When the class loader does not define the annotation type
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A synthesize(AnnotationValue<A> annotationValue, ClassLoader classLoader) {
        Class<?> type = ClassUtils.forName(annotationValue.getAnnotationName(), classLoader)
            .orElseThrow(() -> new IllegalArgumentException("The annotation type " + annotationValue.getAnnotationName()
                + " is not defined by the class loader " + classLoader));
        if (!type.isAnnotation()) {
            throw new IllegalArgumentException("The type " + type.getName() + " is not an annotation");
        }
        return synthesize((Class<A>) type, annotationValue);
    }

    /**
     * Adds one annotation of an element, an annotation whose values cannot be read left out rather than failing
     * the element. An annotation naming a class that is absent from the class path is read by the processor and
     * recorded, while reading it reflectively throws; the entry points a caller asks a named annotation of still
     * propagate, since the caller has nothing else to be given.
     */
    private static void addAnnotationOf(MutableAnnotationMetadata metadata,
                                        AnnotatedElement element,
                                        Annotation annotation,
                                        boolean declared) {
        try {
            addAnnotation(metadata, annotation, declared);
        } catch (RuntimeException e) {
            ClassUtils.REFLECTION_LOGGER.debug("Skipping the annotation [{}] of [{}], its values cannot be read",
                annotation.annotationType().getName(), element, e);
        }
    }

    /**
     * @return The name the metadata files an annotation type under: the container of a repeatable annotation, or
     * the type itself
     */
    private static String filedUnder(Class<? extends Annotation> type) {
        Repeatable repeatable = type.getAnnotation(Repeatable.class);
        return repeatable == null ? type.getName() : repeatable.value().getName();
    }

    /**
     * @return Whether an annotation type is meta-annotated {@link Inherited}, which is what makes the processors
     * keep it on a type that does not declare it
     */
    private static boolean isInheritable(Class<? extends Annotation> type) {
        if (type.isAnnotationPresent(Inherited.class)) {
            return true;
        }
        // a repeatable annotation written more than once is the container the compiler generates, which carries
        // no meta-annotation of its own: the annotation it holds is what says whether it is inherited
        for (Method member : MEMBERS.get(type)) {
            if (AnnotationMetadata.VALUE_MEMBER.equals(member.getName())) {
                Class<?> returnType = member.getReturnType();
                return returnType.isArray()
                    && returnType.getComponentType().isAnnotation()
                    && returnType.getComponentType().isAnnotationPresent(Inherited.class);
            }
        }
        return false;
    }

    /**
     * The interfaces of the hierarchy of a type, breadth first from the type down its super class chain and then
     * up the super interfaces, each interface once: the order a nearer declaration is seen before a farther one.
     */
    private static List<Class<?>> interfaceHierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Class<?> interfaceType : current.getInterfaces()) {
                if (visited.add(interfaceType)) {
                    queue.add(interfaceType);
                }
            }
        }
        while (!queue.isEmpty()) {
            Class<?> interfaceType = queue.poll();
            hierarchy.add(interfaceType);
            for (Class<?> superInterface : interfaceType.getInterfaces()) {
                if (visited.add(superInterface)) {
                    queue.add(superInterface);
                }
            }
        }
        return hierarchy;
    }

    private static void addAnnotation(MutableAnnotationMetadata metadata, Annotation annotation, boolean declared) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (isIgnored(type)) {
            return;
        }
        Annotation[] contained = containedAnnotations(annotation);
        if (contained != null) {
            for (Annotation repeated : contained) {
                addRepeated(metadata, repeated, type, declared);
            }
            return;
        }
        Repeatable repeatable = type.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            addRepeated(metadata, annotation, repeatable.value(), declared);
            return;
        }
        register(metadata, type);
        String name = type.getName();
        if (declared) {
            metadata.addDeclaredAnnotation(name, values(annotation));
        } else {
            metadata.addAnnotation(name, values(annotation));
        }
        addStereotypes(metadata, type, List.of(name), declared);
    }

    private static void addRepeated(MutableAnnotationMetadata metadata,
                                    Annotation annotation,
                                    Class<? extends Annotation> container,
                                    boolean declared) {
        Class<? extends Annotation> type = annotation.annotationType();
        register(metadata, type);
        register(metadata, container);
        DefaultAnnotationMetadata.registerRepeatableAnnotations(Map.of(type.getName(), container.getName()));
        AnnotationValue<?> value = valueOf(annotation);
        if (declared) {
            metadata.addDeclaredRepeatable(container.getName(), value);
        } else {
            metadata.addRepeatable(container.getName(), value);
        }
        addStereotypes(metadata, type, List.of(type.getName()), declared);
    }

    /**
     * Adds the meta-annotations of an annotation type as stereotypes of the given parents, recursively. The
     * parents are the chain from the annotation on the element down to the current type, and a type already
     * in the chain is a cycle, which is skipped.
     */
    private static void addStereotypes(MutableAnnotationMetadata metadata,
                                       Class<? extends Annotation> type,
                                       List<String> parents,
                                       boolean declared) {
        for (Annotation meta : type.getDeclaredAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (isIgnored(metaType) || parents.contains(metaType.getName())) {
                continue;
            }
            Annotation[] contained = containedAnnotations(meta);
            if (contained != null) {
                for (Annotation repeated : contained) {
                    addRepeatedStereotype(metadata, repeated, metaType, parents, declared);
                }
                continue;
            }
            Repeatable repeatable = metaType.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                addRepeatedStereotype(metadata, meta, repeatable.value(), parents, declared);
                continue;
            }
            register(metadata, metaType);
            if (declared) {
                metadata.addDeclaredStereotype(parents, metaType.getName(), values(meta));
            } else {
                metadata.addStereotype(parents, metaType.getName(), values(meta));
            }
            addStereotypes(metadata, metaType, chain(parents, metaType), declared);
        }
    }

    private static void addRepeatedStereotype(MutableAnnotationMetadata metadata,
                                              Annotation meta,
                                              Class<? extends Annotation> container,
                                              List<String> parents,
                                              boolean declared) {
        Class<? extends Annotation> metaType = meta.annotationType();
        if (parents.contains(metaType.getName())) {
            return;
        }
        register(metadata, metaType);
        register(metadata, container);
        DefaultAnnotationMetadata.registerRepeatableAnnotations(Map.of(metaType.getName(), container.getName()));
        AnnotationValue<?> value = valueOf(meta);
        if (declared) {
            metadata.addDeclaredRepeatableStereotype(parents, container.getName(), value);
        } else {
            metadata.addRepeatableStereotype(parents, container.getName(), value);
        }
        addStereotypes(metadata, metaType, chain(parents, metaType), declared);
    }

    private static List<String> chain(List<String> parents, Class<? extends Annotation> type) {
        List<String> chain = new ArrayList<>(parents.size() + 1);
        chain.addAll(parents);
        chain.add(type.getName());
        return chain;
    }

    /**
     * Registers the defaults of an annotation type in the metadata under construction; the shared registry the
     * value accessors consult is filled when the defaults are first computed.
     */
    private static void register(MutableAnnotationMetadata metadata, Class<? extends Annotation> type) {
        Map<CharSequence, Object> defaults = defaultValues(type);
        if (!defaults.isEmpty()) {
            metadata.addDefaultAnnotationValues(type.getName(), defaults);
        }
    }

    /**
     * @return The annotations a repeatable container holds, or {@code null} when the annotation is not a container;
     * a container is the {@link Repeatable} one or a nested {@code List} of its enclosing annotation
     */
    private static Annotation @Nullable [] containedAnnotations(Annotation annotation) {
        Class<? extends Annotation> type = annotation.annotationType();
        Method value;
        try {
            value = type.getDeclaredMethod(AnnotationMetadata.VALUE_MEMBER);
        } catch (NoSuchMethodException e) {
            return null;
        }
        Class<?> returnType = value.getReturnType();
        if (!returnType.isArray() || !returnType.getComponentType().isAnnotation()) {
            return null;
        }
        Class<?> contained = returnType.getComponentType();
        Repeatable repeatable = contained.getAnnotation(Repeatable.class);
        if (repeatable == null || repeatable.value() != type) {
            // a nested `List` of its enclosing annotation is a container by convention even without @Repeatable:
            // Bean Validation declared its constraint containers that way before Java had repeatable annotations
            if (!("List".equals(type.getSimpleName()) && type.getEnclosingClass() == contained)) {
                return null;
            }
        }
        value.trySetAccessible();
        return (Annotation[]) ReflectionUtils.invokeMethod(annotation, value);
    }

    /**
     * A member value as this module records it, on top of the form the shared conversion gives it.
     *
     * <p>{@link AnnotationValue#of(Annotation)} converts the whole tree, so a member holding an annotation comes
     * out as an {@link AnnotationValue} the shared rules built: it carries the members left at their default, its
     * defaults are not registered, its non binding members are not recorded and no customizer has seen it. Those
     * are the policies of this module and they hold at every level, so the nested annotation is read off the
     * instance and converted by {@link #valueOf(Annotation)}, which recurses; only the shape of the conversion
     * stays the concern of the core API.</p>
     *
     * @param annotation The annotation the member is read from
     * @param member     The member
     * @param value      The value of the member, as the shared conversion gives it
     * @return The value to record
     * @throws IllegalStateException When the member cannot be read
     */
    private static Object memberValue(Annotation annotation, Method member, Object value) {
        if (value instanceof AnnotationValue<?>) {
            if (readMember(annotation, member) instanceof Annotation nested) {
                return valueOf(nested);
            }
        } else if (value instanceof AnnotationValue<?>[]) {
            // an array of annotations is walked element by element: each element is an annotation of its own
            if (readMember(annotation, member) instanceof Annotation[] nested) {
                AnnotationValue<?>[] converted = new AnnotationValue[nested.length];
                for (int i = 0; i < nested.length; i++) {
                    converted[i] = valueOf(nested[i]);
                }
                return converted;
            }
        }
        // a value that is not an annotation is left as the conversion gave it, copied when it is an array
        return copied(value);
    }

    /**
     * A member read off an annotation instance again, to convert the annotation it holds rather than the
     * converted form of it. A failure is reported the way {@link AnnotationValue#of(Annotation)} reports one, so
     * that a member read here fails no differently from the same member read there.
     */
    @Nullable
    private static Object readMember(Annotation annotation, Method member) {
        try {
            return ReflectionUtils.invokeInaccessibleMethod(annotation, member);
        } catch (RuntimeException e) {
            // the reflective wrappers say nothing an invocation target's own exception does not say better
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            throw new IllegalStateException("Cannot read member [" + member.getName() + "] of annotation ["
                + annotation.annotationType().getName() + "]: " + cause, e);
        }
    }

    /**
     * A value copied when it is an array, so that a caller mutating what it is handed cannot reach the array of
     * the annotation.
     *
     * <p>The copy is made here rather than in the core API because the shared conversion keeps an array it has
     * no shape to change - one of primitives or of strings - as the instance answered it, which is safe for the
     * proxy the compiler builds, since that one clones an array member on every call, and is not safe for an
     * annotation written by hand nor for the one {@link #synthesize(Class, AnnotationValue)} builds, both of
     * which answer the same array every time. The values this class builds are handed to a customizer and stored
     * in a metadata, so the array they hold is to be theirs.</p>
     */
    private static Object copied(Object value) {
        if (!value.getClass().isArray()) {
            return value;
        }
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        System.arraycopy(value, 0, copy, 0, length);
        return copy;
    }

    /**
     * The default of a member, in the form the metadata records a value.
     *
     * <p>{@link AnnotationValue#of(Annotation)} converts what it reads off an annotation instance, and a default
     * has no instance behind it: {@link Method#getDefaultValue()} is the only source of it, so the module
     * converts a default itself. The shapes are the ones that method produces, so that a value read off an
     * instance and the default of its member compare equal and the member is left out of the values.</p>
     */
    private static Object convert(Object value) {
        if (value instanceof Class<?> type) {
            return new AnnotationClassValue<>(type);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Annotation annotation) {
            return AnnotationValue.of(annotation);
        }
        if (value instanceof Class<?>[] types) {
            AnnotationClassValue<?>[] converted = new AnnotationClassValue[types.length];
            for (int i = 0; i < types.length; i++) {
                converted[i] = new AnnotationClassValue<>(types[i]);
            }
            return converted;
        }
        if (value instanceof Enum<?>[] constants) {
            String[] converted = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                converted[i] = constants[i].name();
            }
            return converted;
        }
        if (value instanceof Annotation[] annotations) {
            AnnotationValue<?>[] converted = new AnnotationValue[annotations.length];
            for (int i = 0; i < annotations.length; i++) {
                converted[i] = AnnotationValue.of(annotations[i]);
            }
            return converted;
        }
        return value;
    }

    private static boolean isIgnored(Class<? extends Annotation> type) {
        String name = type.getName();
        return name.startsWith(JAVA_LANG_ANNOTATION)
            || name.startsWith(KOTLIN)
            || AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(name);
    }

    /**
     * An annotation instance over an {@link AnnotationValue}, for a type the shared proxy cannot be built for.
     * A member is the value the annotation value carries, converted to the type the member declares, or the
     * default of the member; {@code equals}, {@code hashCode} and {@code toString} follow
     * {@link Annotation}, so that an instance compares equal to the annotation the compiler makes.
     *
     * @param <A> The annotation type
     */
    private static final class SynthesizedAnnotation<A extends Annotation> implements InvocationHandler, AnnotationValueProvider<A> {

        private final Class<A> annotationType;
        private final AnnotationValue<A> annotationValue;

        SynthesizedAnnotation(Class<A> annotationType, AnnotationValue<A> annotationValue) {
            this.annotationType = annotationType;
            this.annotationValue = annotationValue;
        }

        @Override
        public AnnotationValue<A> annotationValue() {
            return annotationValue;
        }

        @Override
        @Nullable
        public Object invoke(Object proxy, Method method, Object @Nullable [] args) {
            String name = method.getName();
            if (args != null && args.length == 1 && "equals".equals(name)) {
                return equalsAnnotation(args[0]);
            }
            if (args == null || args.length == 0) {
                switch (name) {
                    case "hashCode" -> {
                        return annotationHashCode();
                    }
                    case "toString" -> {
                        return annotationValue.toString();
                    }
                    case "annotationType" -> {
                        return annotationType;
                    }
                    default -> {
                        if (method.getReturnType() == AnnotationValue.class) {
                            return annotationValue;
                        }
                        return member(method);
                    }
                }
            }
            return member(method);
        }

        @Nullable
        private Object member(Method member) {
            Object value = annotationValue.getValues().get(member.getName());
            if (value == null) {
                return member.getDefaultValue();
            }
            Object converted = ConversionService.SHARED
                .convert(value, Argument.of(member.getReturnType()))
                .orElse(null);
            return converted == null ? member.getDefaultValue() : converted;
        }

        private boolean equalsAnnotation(@Nullable Object other) {
            if (other == null || !annotationType.isInstance(other)) {
                return false;
            }
            if (other instanceof AnnotationValueProvider<?> provider) {
                return annotationValue.equals(provider.annotationValue());
            }
            for (Method declared : MEMBERS.get(annotationType)) {
                Object mine = member(declared);
                Object theirs = ReflectionUtils.invokeMethod(other, declared);
                if (!Objects.deepEquals(mine, theirs)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The hash code {@link Annotation#hashCode()} defines: the sum over the members of the hash of the
         * member name times 127, exclusive-ored with the hash of its value.
         */
        private int annotationHashCode() {
            int hashCode = 0;
            for (Method declared : MEMBERS.get(annotationType)) {
                Object value = member(declared);
                int valueHash = value == null ? 0 : (value.getClass().isArray() ? arrayHashCode(value) : value.hashCode());
                hashCode += (127 * declared.getName().hashCode()) ^ valueHash;
            }
            return hashCode;
        }

        private static int arrayHashCode(Object array) {
            int length = Array.getLength(array);
            int hashCode = 1;
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                hashCode = 31 * hashCode + (element == null ? 0 : element.hashCode());
            }
            return hashCode;
        }
    }
}
