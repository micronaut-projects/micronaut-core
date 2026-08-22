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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ReflectionUtils;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds {@link AnnotationMetadata} from the annotations read reflectively from an
 * {@link AnnotatedElement}, in the shape the annotation processors give the metadata they generate at
 * compilation time.
 *
 * <p>Code written against generated metadata then works unchanged for an element that has none:</p>
 * <ul>
 *     <li>the meta-annotations of an annotation are its stereotypes, recursively, with their values;</li>
 *     <li>a repeatable annotation is filed under its container, whether it was written once, several times or
 *     inside the container, and the container is registered with {@link AnnotationMetadataSupport} so that
 *     {@link AnnotationMetadata#getAnnotationValuesByType(Class)} finds it;</li>
 *     <li>the defaults of the members are registered and reachable through
 *     {@link AnnotationMetadata#getDefaultValues(String)} and the value accessors;</li>
 *     <li>a class value is an {@link AnnotationClassValue}, an enum value is its name, a nested annotation is an
 *     {@link AnnotationValue};</li>
 *     <li>an annotation inherited by a class through {@link java.lang.annotation.Inherited} is present but not
 *     declared.</li>
 * </ul>
 *
 * <p>An annotation instance does not say which members were written, so a member whose value equals its
 * default is not part of the values of the annotation; it is served by the defaults, as a member that was not
 * written is at compilation time.</p>
 *
 * <p>The annotation mappers, transformers and remappers of the annotation processors are not applied: they
 * are part of {@code micronaut-core-processor} and are not on a runtime classpath. The
 * {@link ReflectionAnnotationCustomizer} services are their runtime counterpart, and receive the values of
 * every annotation the builder converts.</p>
 *
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class ReflectionAnnotationMetadataBuilder {

    private static final String JAVA_LANG_ANNOTATION = "java.lang.annotation.";
    private static final String KOTLIN = "kotlin.";
    private static final List<ReflectionAnnotationCustomizer> CUSTOMIZERS =
        SoftServiceLoader.load(ReflectionAnnotationCustomizer.class).collectAll();

    private ReflectionAnnotationMetadataBuilder() {
    }

    /**
     * Builds the metadata of an element.
     *
     * @param element The element
     * @return The metadata, {@link AnnotationMetadata#EMPTY_METADATA} when the element has no annotation
     */
    public static AnnotationMetadata build(AnnotatedElement element) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        add(metadata, element);
        return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
    }

    /**
     * Builds the metadata of several elements merged, the first element first: the field, the getter and the
     * setter of a property, or a parameter and its type.
     *
     * @param elements The elements
     * @return The metadata, {@link AnnotationMetadata#EMPTY_METADATA} when none of the elements has an annotation
     */
    public static AnnotationMetadata build(AnnotatedElement... elements) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        for (AnnotatedElement element : elements) {
            if (element != null) {
                add(metadata, element);
            }
        }
        return metadata.isEmpty() ? AnnotationMetadata.EMPTY_METADATA : metadata;
    }

    /**
     * Adds the annotations of an element to a metadata under construction.
     *
     * @param metadata The metadata
     * @param element  The element
     */
    public static void add(MutableAnnotationMetadata metadata, AnnotatedElement element) {
        Annotation[] declared = element.getDeclaredAnnotations();
        for (Annotation annotation : declared) {
            addAnnotation(metadata, annotation, true);
        }
        if (element instanceof Class<?>) {
            Set<Annotation> declaredSet = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            declaredSet.addAll(Arrays.asList(declared));
            for (Annotation annotation : element.getAnnotations()) {
                if (!declaredSet.contains(annotation)) {
                    addAnnotation(metadata, annotation, false);
                }
            }
        }
    }

    /**
     * Adds one annotation instance to a metadata under construction, as {@link #add(MutableAnnotationMetadata, AnnotatedElement)}
     * adds every annotation of an element: a caller keeping only some annotations of an element — the
     * constraints, the qualifiers — filters the instances and adds the ones it keeps.
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
     * name and a nested annotation is an {@link AnnotationValue}.
     *
     * @param annotation The annotation
     * @return The values
     */
    public static Map<CharSequence, Object> values(Annotation annotation) {
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        Class<? extends Annotation> type = annotation.annotationType();
        for (Method member : members(type)) {
            Object value = ReflectionUtils.invokeMethod(annotation, member);
            Object defaultValue = member.getDefaultValue();
            if (value == null || (defaultValue != null && Objects.deepEquals(value, defaultValue))) {
                continue;
            }
            values.put(member.getName(), convert(value));
        }
        for (ReflectionAnnotationCustomizer customizer : CUSTOMIZERS) {
            if (customizer.supports(type)) {
                customizer.customize(annotation, values);
            }
        }
        return values;
    }

    /**
     * The defaults of the members of an annotation type, converted as {@link #values(Annotation)} converts
     * the values.
     *
     * @param annotationType The annotation type
     * @return The defaults
     */
    public static Map<CharSequence, Object> defaultValues(Class<? extends Annotation> annotationType) {
        Map<CharSequence, Object> defaults = new LinkedHashMap<>();
        for (Method member : members(annotationType)) {
            Object defaultValue = member.getDefaultValue();
            if (defaultValue != null) {
                defaults.put(member.getName(), convert(defaultValue));
            }
        }
        return defaults;
    }

    /**
     * The annotation value of an annotation instance, carrying its defaults. An instance that is an
     * {@link AnnotationValueProvider} — a synthetic annotation built from a value — returns that value.
     *
     * @param annotation The annotation
     * @param <A>        The annotation type
     * @return The annotation value
     */
    public static <A extends Annotation> AnnotationValue<A> annotationValue(A annotation) {
        return annotationValue(annotation, null);
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
    public static <A extends Annotation> AnnotationValue<A> annotationValue(A annotation,
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
        AnnotationMetadataSupport.registerRepeatableAnnotation(type.getName(), container.getName());
        AnnotationValue<?> value = annotationValue(annotation);
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
        AnnotationMetadataSupport.registerRepeatableAnnotation(metaType.getName(), container.getName());
        AnnotationValue<?> value = annotationValue(meta);
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
     * Registers the defaults of an annotation type, both in the metadata under construction and in the
     * shared registry the value accessors consult. The registry is keyed by name and the annotation type
     * itself is not registered: a type loaded by several class loaders — the same annotation in several
     * deployments — must resolve to the class of the loader asking for it, not to the first one seen.
     */
    private static void register(MutableAnnotationMetadata metadata, Class<? extends Annotation> type) {
        Map<CharSequence, Object> defaults = defaultValues(type);
        AnnotationMetadataSupport.registerDefaultValues(type.getName(), defaults);
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

    private static List<Method> members(Class<? extends Annotation> type) {
        List<Method> members = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && !method.isSynthetic() && method.getParameterCount() == 0) {
                // the annotation type may not be public, its members are read all the same
                method.trySetAccessible();
                members.add(method);
            }
        }
        members.sort(Comparator.comparing(Method::getName));
        return members;
    }

    private static Object convert(Object value) {
        if (value instanceof Class<?> type) {
            return new AnnotationClassValue<>(type);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Annotation annotation) {
            return annotationValue(annotation);
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
                converted[i] = annotationValue(annotations[i]);
            }
            return converted;
        }
        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            // an array of strings, kept as it is, copied so that the annotation instance is not shared
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            System.arraycopy(value, 0, copy, 0, length);
            return copy;
        }
        return value;
    }

    private static boolean isIgnored(Class<? extends Annotation> type) {
        String name = type.getName();
        return name.startsWith(JAVA_LANG_ANNOTATION)
            || name.startsWith(KOTLIN)
            || AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(name);
    }
}
